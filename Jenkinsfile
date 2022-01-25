#!groovy

def workerNode = "devel10"

void notifyOfBuildStatus(final String buildStatus) {
    final String subject = "${buildStatus}: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
    final String details = """<p> Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]':</p>
    <p>Check console output at &QUOT;<a href='${env.BUILD_URL}'>${env.JOB_NAME} [${env.BUILD_NUMBER}]</a>&QUOT;</p>"""
    emailext(
            subject: "$subject",
            body: "$details", attachLog: true, compressLog: false,
            mimeType: "text/html",
            recipientProviders: [[$class: "CulpritsRecipientProvider"]]
    )
}

pipeline {
    agent {label workerNode}

    options {
        timestamps()
    }

    triggers {
        pollSCM('H/20 * * * *')
    }

    environment {
        DOCKER_IMAGE_VERSION = "${env.BRANCH_NAME}-${env.BUILD_NUMBER}"
        DOCKER_IMAGE_DIT_VERSION = "DIT-${env.BUILD_NUMBER}"
        GITOPS_DEPLOY_TAG = "master-5"
        GITLAB_PRIVATE_TOKEN = credentials("metascrum-gitlab-api-token")
    }

	tools {
		jdk 'jdk11'
		maven 'Maven 3'
	}

    stages {
        stage('Clear workspace') {
            steps {
                deleteDir()
                checkout scm
            }
        }

        stage('Build updateservice') {
            steps {
                withMaven(maven: 'maven 3.5', options: [
                        findbugsPublisher(disabled: true),
                        openTasksPublisher(highPriorityTaskIdentifiers: 'todo', ignoreCase: true, lowPriorityTaskIdentifiers: 'review', normalPriorityTaskIdentifiers: 'fixme,fix')
                ]) {
                    sh "mvn verify pmd:pmd findbugs:findbugs"
                    archiveArtifacts(artifacts: "target/*.war,target/*.log", onlyIfSuccessful: true, fingerprint: true)
                    junit "**/target/surefire-reports/TEST-*.xml,**/target/failsafe-reports/TEST-*.xml"
                }
            }
        }

        stage('Warnings') {
            steps {
                warnings consoleParsers: [
                        [parserName: "Java Compiler (javac)"],
                        [parserName: "JavaDoc Tool"]
                ],
                        unstableTotalAll: "0",
                        failedTotalAll: "0"
            }
        }

        stage('PMD') {
            steps {
                step([
                        $class          : 'hudson.plugins.pmd.PmdPublisher',
                        pattern         : '**/target/pmd.xml',
                        unstableTotalAll: "0",
                        failedTotalAll  : "0"
                ])
            }
        }

        stage('Docker') {
            when {
                expression {
                    currentBuild.result == null || currentBuild.result == 'SUCCESS'
                }
            }
            steps {
                script {
                    echo "Using branch: \"${env.BRANCH_NAME}\""
                    echo "JOBNAME: \"${env.JOB_NAME}\""
                    echo "GIT_COMMIT: \"${env.GIT_COMMIT}\""
                    echo "BUILD_NUMBER: \"${env.BUILD_NUMBER}\""
                    echo "IMAGE VERSION: \"${DOCKER_IMAGE_VERSION}\""

                    docker.build("docker-i.dbc.dk/update-postgres:${DOCKER_IMAGE_VERSION}",
                            "--label jobname=${env.JOB_NAME} " +
                                    "--label gitcommit=${env.GIT_COMMIT} " +
                                    "--label buildnumber=${env.BUILD_NUMBER} " +
                                    "--label user=isworker " +
                                    "--pull --no-cache docker/update-postgres/")

                    docker.build("docker-i.dbc.dk/update-payara:${DOCKER_IMAGE_VERSION}",
                            "--label jobname=${env.JOB_NAME} " +
                                    "--label gitcommit=${env.GIT_COMMIT} " +
                                    "--label buildnumber=${env.BUILD_NUMBER} " +
                                    "--label user=isworker " +
                                    "--pull --no-cache docker/update-payara/")

                    docker.build("docker-i.dbc.dk/update-payara-deployer:${DOCKER_IMAGE_VERSION}",
                            "--label jobname=${env.JOB_NAME} " +
                                    "--label gitcommit=${env.GIT_COMMIT} " +
                                    "--label buildnumber=${env.BUILD_NUMBER} " +
                                    "--label user=isworker " +
                                    "--build-arg PARENT_IMAGE=docker-i.dbc.dk/update-payara:${DOCKER_IMAGE_VERSION} " +
                                    "--build-arg BUILD_NUMBER=${env.BUILD_NUMBER} " +
                                    "--build-arg BRANCH_NAME=${env.BRANCH_NAME} " +
                                    "--no-cache docker/update-payara-deployer/")

                    docker.build("docker-i.dbc.dk/ocb-tools-deployer:${DOCKER_IMAGE_VERSION}",
                            "--label jobname=${env.JOB_NAME} " +
                                    "--label gitcommit=${env.GIT_COMMIT} " +
                                    "--label buildnumber=${env.BUILD_NUMBER} " +
                                    "--label user=isworker " +
                                    "--build-arg BUILD_NUMBER=${env.BUILD_NUMBER} " +
                                    "--build-arg BRANCH_NAME=${env.BRANCH_NAME} " +
                                    "--pull --no-cache docker/ocb-tools-deployer/")
                }
            }
        }

        stage('Run systemtest') {
            when {
                expression {
                    currentBuild.result == null || currentBuild.result == 'SUCCESS'
                }
            }
            steps {
                lock('meta-updateservice-systemtest') {
                    sh "bin/envsubst.sh ${DOCKER_IMAGE_VERSION}"
                    sh "./system-test.sh payara"

                    junit "docker/deployments/systemtests-payara/logs/ocb-tools/TEST-*.xml"
                    archiveArtifacts(artifacts: "docker/deployments/systemtests-payara/logs/*.log", onlyIfSuccessful: false, fingerprint: true)
                }
            }
        }

        stage('Push docker') {
            when {
                expression {
                    currentBuild.result == null || currentBuild.result == 'SUCCESS'
                }
            }
            steps {
                script {
                    sh """
                        docker push docker-i.dbc.dk/update-postgres:${DOCKER_IMAGE_VERSION}
                        docker push docker-i.dbc.dk/update-payara:${DOCKER_IMAGE_VERSION}
                        docker push docker-i.dbc.dk/update-payara-deployer:${DOCKER_IMAGE_VERSION}
                    """

                    if (env.BRANCH_NAME == 'master') {
                        sh """
                            docker tag docker-i.dbc.dk/update-postgres:${DOCKER_IMAGE_VERSION} docker-i.dbc.dk/update-postgres:${DOCKER_IMAGE_DIT_VERSION}
                            docker push docker-i.dbc.dk/update-postgres:${DOCKER_IMAGE_DIT_VERSION}

                            docker tag docker-i.dbc.dk/update-postgres:${DOCKER_IMAGE_VERSION} docker-i.dbc.dk/update-postgres:staging
                            docker push docker-i.dbc.dk/update-postgres:staging

                            docker tag docker-i.dbc.dk/update-payara-deployer:${DOCKER_IMAGE_VERSION} docker-i.dbc.dk/update-payara-deployer:${DOCKER_IMAGE_DIT_VERSION}
                            docker push docker-i.dbc.dk/update-payara-deployer:${DOCKER_IMAGE_DIT_VERSION}

                            docker tag docker-i.dbc.dk/update-payara-deployer:${DOCKER_IMAGE_VERSION} docker-i.dbc.dk/update-payara-deployer:staging
                            docker push docker-i.dbc.dk/update-payara-deployer:staging
                        """
                    }
                }
            }
        }

        stage("Deploy k8s") {
            agent {
                docker {
                    label workerNode
                    image "docker.dbc.dk/build-env:latest"
                    alwaysPull true
                }
            }
            when {
                expression {
                    (currentBuild.result == null || currentBuild.result == 'SUCCESS') && env.BRANCH_NAME == 'master'
                }
            }
            steps {
                script {
                    dir("deploy") {
                        sh """
                            set-new-version services/update-service-tmpl.yml ${env.GITLAB_PRIVATE_TOKEN} metascrum/dit-gitops-secrets ${DOCKER_IMAGE_DIT_VERSION} -b master
                            set-new-version databases/update-database.yml ${env.GITLAB_PRIVATE_TOKEN} metascrum/dit-gitops-secrets ${DOCKER_IMAGE_DIT_VERSION} -b master

							set-new-version update-service.yml ${env.GITLAB_PRIVATE_TOKEN} metascrum/updateservice-deploy ${DOCKER_IMAGE_DIT_VERSION} -b basismig
                            set-new-version update-service.yml ${env.GITLAB_PRIVATE_TOKEN} metascrum/updateservice-deploy ${DOCKER_IMAGE_DIT_VERSION} -b fbstest
                            set-new-version update-service.yml ${env.GITLAB_PRIVATE_TOKEN} metascrum/updateservice-deploy ${DOCKER_IMAGE_DIT_VERSION} -b metascrum-staging
						"""
                    }
                }
            }
        }
    }

    post {
        unstable {
            notifyOfBuildStatus("Jenkins build became unstable")
        }
        failure {
            notifyOfBuildStatus("Jenkins build failed")
        }
        fixed {
            notifyOfBuildStatus("Jenkins build is back to normal")
        }
        always {
            sh """
                docker/bin/remove-image.sh docker-i.dbc.dk/update-postgres:${DOCKER_IMAGE_VERSION}
                docker/bin/remove-image.sh docker-i.dbc.dk/update-postgres:${DOCKER_IMAGE_DIT_VERSION}
                docker/bin/remove-image.sh docker-i.dbc.dk/update-postgres:staging

                docker/bin/remove-image.sh docker-i.dbc.dk/update-payara:${DOCKER_IMAGE_VERSION}

                docker/bin/remove-image.sh docker-i.dbc.dk/update-payara-deployer:${DOCKER_IMAGE_VERSION}
                docker/bin/remove-image.sh docker-i.dbc.dk/update-payara-deployer:${DOCKER_IMAGE_DIT_VERSION}
                docker/bin/remove-image.sh docker-i.dbc.dk/update-payara-deployer:staging

                docker/bin/remove-image.sh docker-i.dbc.dk/ocb-tools-deployer:${DOCKER_IMAGE_VERSION}
            """

            deleteDir()
        }
    }
}
