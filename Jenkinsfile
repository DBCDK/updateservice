#!groovy

def workerNode = "devel8"

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

void deploy(String deployEnvironment) {
	dir("deploy") {
		git(url: "gitlab@git-platform.dbc.dk:metascrum/deploy.git", credentialsId: "gitlab-meta")
	}
	sh """
        bash -c '
            virtualenv -p python3 .
            source bin/activate
            pip3 install --upgrade pip
            pip3 install -U -e \"git+https://github.com/DBCDK/mesos-tools.git#egg=mesos-tools\"
            marathon-config-producer updateservice-${deployEnvironment} --root deploy/marathon --template-keys DOCKER_TAG=${DOCKER_IMAGE_VERSION} -o updateservice-${deployEnvironment}.json
            marathon-deployer -a ${MARATHON_TOKEN} -b https://mcp1.dbc.dk:8443 deploy updateservice-${deployEnvironment}.json
        '
	"""
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
        MARATHON_TOKEN = credentials("METASCRUM_MARATHON_TOKEN")
        DOCKER_IMAGE_VERSION = "${env.BRANCH_NAME}-${env.BUILD_NUMBER}"
        DOCKER_IMAGE_DIT_VERSION = "DIT-${env.BUILD_NUMBER}"
        GITOPS_DEPLOY_TAG = "master-3"
        GITLAB_PRIVATE_TOKEN = credentials("metascrum-gitlab-api-token")
    }

    tools {
        maven 'maven 3.5'
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
                                    "docker/update-postgres/")

                    docker.build("docker-i.dbc.dk/update-payara:${DOCKER_IMAGE_VERSION}",
                            "--label jobname=${env.JOB_NAME} " +
                                    "--label gitcommit=${env.GIT_COMMIT} " +
                                    "--label buildnumber=${env.BUILD_NUMBER} " +
                                    "--label user=isworker " +
                                    "docker/update-payara/")

                    docker.build("docker-i.dbc.dk/update-payara-deployer:${DOCKER_IMAGE_VERSION}",
                            "--label jobname=${env.JOB_NAME} " +
                                    "--label gitcommit=${env.GIT_COMMIT} " +
                                    "--label buildnumber=${env.BUILD_NUMBER} " +
                                    "--label user=isworker " +
                                    "--build-arg PARENT_IMAGE=docker-i.dbc.dk/update-payara:${DOCKER_IMAGE_VERSION} " +
                                    "--build-arg BUILD_NUMBER=${env.BUILD_NUMBER} " +
                                    "--build-arg BRANCH_NAME=${env.BRANCH_NAME} " +
                                    "docker/update-payara-deployer/")

                    docker.build("docker-i.dbc.dk/ocb-tools-deployer:${DOCKER_IMAGE_VERSION}",
                            "--label jobname=${env.JOB_NAME} " +
                                    "--label gitcommit=${env.GIT_COMMIT} " +
                                    "--label buildnumber=${env.BUILD_NUMBER} " +
                                    "--label user=isworker " +
                                    "--build-arg BUILD_NUMBER=${env.BUILD_NUMBER} " +
                                    "--build-arg BRANCH_NAME=${env.BRANCH_NAME} " +
                                    "docker/ocb-tools-deployer/")
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

        stage("Deploy mesos") {
            when {
                expression {
                    (currentBuild.result == null || currentBuild.result == 'SUCCESS') && env.BRANCH_NAME == 'master'
                }
            }
            steps {
                script {
                    lock('meta-updateservice-deploy-staging') {
                        deploy("staging-basismig")
                        deploy("staging-fbs")
                    }
                }
            }
        }

        stage("Deploy k8s") {
            agent {
                docker {
                    label workerNode
                    image "docker.dbc.dk/gitops-deploy-env:${env.GITOPS_DEPLOY_TAG}"
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
							set-new-version update-service.yml ${env.GITLAB_PRIVATE_TOKEN} metascrum/updateservice-deploy ${DOCKER_IMAGE_DIT_VERSION} -b basismig
                            set-new-version update-service.yml ${env.GITLAB_PRIVATE_TOKEN} metascrum/updateservice-deploy ${DOCKER_IMAGE_DIT_VERSION} -b fbstest
						"""
                    }
                }
            }
        }
    }

    post {
        unstable {
            notifyOfBuildStatus("build became unstable")
        }
        failure {
            notifyOfBuildStatus("build failed")
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