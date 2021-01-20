// DEV_SOURCE = symphony-helm/cicd/flow.groovy

@Library("bellevue-ci-libraries") _

import com.vmware.devops.GitHelpers
import com.vmware.devops.GitLabProjects

// Job configuration
def jobProperties = [
    [$class: "org.jenkinsci.plugins.workflow.job.properties.DisableConcurrentBuildsJobProperty"],
    [$class: "hudson.model.ParametersDefinitionProperty",
     parameterDefinitions: [
         [$class: "hudson.model.StringParameterDefinition",
          name: "symphonyHelmChangeset",
          defaultValue: "origin/$BRANCH_NAME"
         ],
         [$class: "hudson.model.BooleanParameterDefinition",
          name: "forceBuild",
          defaultValue: false
         ]
     ]
    ],
    [$class: "BuildDiscarderProperty",
     strategy: [
         $class: 'LogRotator',
         numToKeepStr: '50'
     ]
    ]
]

properties(jobProperties)

symphonyHelmChangeset = null
forceBuild = null
if (!params.keySet().contains("symphonyHelmChangeset") ||
    isEmpty(params.symphonyHelmChangeset)) {
    symphonyHelmChangeset = "origin/$BRANCH_NAME"
    forceBuild = "false"
} else {
    symphonyHelmChangeset = params.symphonyHelmChangeset
    forceBuild = params.forceBuild
}
// =============

def runBuild (String job, boolean failOnError = true) {
    try {
        build (job: "/${job}/${BRANCH_NAME.replace("/","%2F")}", wait: failOnError, parameters: [
            [$class: 'StringParameterValue', name: "symphonyHelmChangeset", value: symphonyHelmChangeset],
            [$class: 'BooleanParameterValue', name: "forceBuild", value: forceBuild]
        ])
    } catch (any) {
        addErrorBadge(env.STAGE_NAME)
        echo("'${env.STAGE_NAME}' failed with error '$any'")
        if (failOnError) {
            throw any
        }
    }
}

timestamps {

    stage ("Resolve changeset") {
        node("default") {
            //Checkout required to properly configure the scm trigger
            dir("symphony-helm") {
                gitlabCheckout(
                    projectName: GitLabProjects.SYMPHONY_HELM,
                    shallow: false,
                    changeset: symphonyHelmChangeset,
                    poll: true
                )
                symphonyHelmChangeset = new GitHelpers().getChangeset()
            }
        }

        identifiers = [
            "symphonyHelmChangeset" : symphonyHelmChangeset
        ]

        addBuildIdBadge(identifiers)
    }

    if(params.skipE2E != "true") {
        stage("Run tests") {
            parallel( // VDEVOPS-2950
                "traefik": {
                    runBuild("symphony-helm-traefik")
                },
                "heimdall": {
                    runBuild("symphony-helm-heimdall")
                }
            )
        }
    }

    stage ("Promote") {
        runBuild("symphony-helm-promotion")
    }
}
