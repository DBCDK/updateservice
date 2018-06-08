#!groovy

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

dockerImageVersion = "${env.BRANCH_NAME}-${env.BUILD_NUMBER}"
dockerImageDITVersion = "DIT-${env.BUILD_NUMBER}"

pipeline {
    agent { label 'itwn-002' }

    options {
        buildDiscarder(logRotator(numToKeepStr: '30', daysToKeepStr: '20'))
        disableConcurrentBuilds()
        timeout(time: 1, unit: 'HOURS')
        timestamps()
    }

    triggers {
        pollSCM('H/3 * * * *')
        upstream(upstreamProjects: "updateservice/opencat-business/master",
        			threshold: hudson.model.Result.SUCCESS)
    }

    environment {
        MAVEN_OPTS = "-XX:+TieredCompilation -XX:TieredStopAtLevel=1 -Dorg.slf4j.simpleLogger.showThreadName=true"
        JAVA_OPTS = "-XX:-UseSplitVerifier"
        VERSION = readMavenPom().getVersion()
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
                    echo "Using branch ${env.BRANCH_NAME}"
                    echo "JOBNAME: \"${env.JOB_NAME}\""
                    echo "GIT_COMMIT: \"${env.GIT_COMMIT}\""
                    echo "BUILD_NUMBER: \"${env.BUILD_NUMBER}\""
                    echo "IMAGE VERSION: \"${dockerImageVersion}\""

                    docker.build("docker-i.dbc.dk/update-postgres:${dockerImageVersion}",
                            "--label jobname=${env.JOB_NAME} " +
                                    "--label gitcommit=${env.GIT_COMMIT} " +
                                    "--label buildnumber=${env.BUILD_NUMBER} " +
                                    "--label user=isworker " +
                                    "docker/update-postgres/")

                    docker.build("docker-i.dbc.dk/update-payara:${dockerImageVersion}",
                            "--label jobname=${env.JOB_NAME} " +
                                    "--label gitcommit=${env.GIT_COMMIT} " +
                                    "--label buildnumber=${env.BUILD_NUMBER} " +
                                    "--label user=isworker " +
                                    "docker/update-payara/")

                    docker.build("docker-i.dbc.dk/update-payara-deployer:${dockerImageVersion}",
                            "--label jobname=${env.JOB_NAME} " +
                                    "--label gitcommit=${env.GIT_COMMIT} " +
                                    "--label buildnumber=${env.BUILD_NUMBER} " +
                                    "--label user=isworker " +
                                    "--build-arg PARENT_IMAGE=docker-i.dbc.dk/update-payara:${dockerImageVersion} " +
                                    "--build-arg BUILD_NUMBER=${env.BUILD_NUMBER} " +
                                    "--build-arg BRANCH_NAME=${env.BRANCH_NAME} " +
                                    "docker/update-payara-deployer/")

                    docker.build("docker-i.dbc.dk/ocb-tools-deployer:${dockerImageVersion}",
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
                sh "bin/envsubst.sh ${dockerImageVersion}"
                sh "./system-test.sh payara"

                junit "docker/deployments/systemtests-payara/logs/ocb-tools/TEST-*.xml"
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
                        docker push docker-i.dbc.dk/update-postgres:${dockerImageVersion}
                        docker push docker-i.dbc.dk/update-payara:${dockerImageVersion}
                        docker push docker-i.dbc.dk/update-payara-deployer:${dockerImageVersion}
                    """

                    if (env.BRANCH_NAME == 'master') {
                        sh """
                            docker tag docker-i.dbc.dk/update-postgres:${dockerImageVersion} docker-i.dbc.dk/update-postgres:${dockerImageDITVersion}
                            docker push docker-i.dbc.dk/update-postgres:${dockerImageDITVersion}

                            docker tag docker-i.dbc.dk/update-payara-deployer:${dockerImageVersion} docker-i.dbc.dk/update-payara-deployer:${dockerImageDITVersion}
                            docker push docker-i.dbc.dk/update-payara-deployer:${dockerImageDITVersion}
                        """
                    }
                }
            }
        }

        stage('Deploy staging') {
            when {
                expression {
                    currentBuild.result == null || currentBuild.result == 'SUCCESS'
                }
            }
            steps {
                echo "Should be deploying here..."
                dir('dit') {
                    git(url: "https://github.com/DBCDK/dit")
                    sh 'kubernetes/handle_deployment.sh'
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
                docker/bin/remove-image.sh docker-i.dbc.dk/update-postgres:${dockerImageVersion}
                docker/bin/remove-image.sh docker-i.dbc.dk/update-postgres:${dockerImageDITVersion}

                docker/bin/remove-image.sh docker-i.dbc.dk/update-payara:${dockerImageVersion}

                docker/bin/remove-image.sh docker-i.dbc.dk/update-payara-deployer:${dockerImageVersion}
                docker/bin/remove-image.sh docker-i.dbc.dk/update-payara-deployer:${dockerImageDITVersion}

                docker/bin/remove-image.sh docker-i.dbc.dk/ocb-tools-deployer:${dockerImageVersion}
            """
        }
    }

}