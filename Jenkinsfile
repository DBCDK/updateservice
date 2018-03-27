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
        stage('Build') {
            steps {
                script {
                    withMaven(maven: 'maven 3.5', options: [
                            findbugsPublisher(disabled: true),
                            openTasksPublisher(highPriorityTaskIdentifiers: 'todo', ignoreCase: true, lowPriorityTaskIdentifiers: 'review', normalPriorityTaskIdentifiers: 'fixme,fix')
                    ]) {
                        sh "mvn clean install -Dmaven.test.failure.ignore=false"
                        junit "**/target/surefire-reports/TEST-*.xml,**/target/failsafe-reports/TEST-*.xml"
                        archiveArtifacts(artifacts: "target/*.war, target/*.log", onlyIfSuccessful: true, fingerprint: true)
                    }
                }
            }
        }

        stage('Docker') {
            steps {
                script {
                    def isMasterBranch = env.BRANCH_NAME == 'master'
                    def dockerFiles = ['docker/update-postgres', 'docker/update-payara', 'docker/update-payara-deployer']

                    echo "Using branch ${env.BRANCH_NAME}"

                    for (def dockerFile : dockerFiles) {
                        def imageName = dockerFile.split('/')[1].toLowerCase()
                        echo "Building docker image ${imageName}"

                        def imageLabel = env.BUILD_NUMBER

                        if (!isMasterBranch) {
                            imageLabel = env.BRANCH_NAME + '-' + env.BUILD_NUMBER
                        }

                        echo "JOBNAME: \"${env.JOB_NAME}\""
                        echo "GIT_COMMIT: \"${env.GIT_COMMIT}\""
                        echo "BUILD_NUMBER:\" ${env.BUILD_NUMBER}\""

                        def image = docker.build("docker-i.dbc.dk/${imageName}:${imageLabel}",
                                "--label jobname=${env.JOB_NAME} " +
                                        // This label should obviously be called "git" but we need to understand what the label is used for first
                                        "--label svn=${env.GIT_COMMIT} " +
                                        "--label buildnumber=${env.BUILD_NUMBER} " +
                                        "--label user=isworker ${dockerFile}")

                        echo "Pushing ${imageName}"
                        image.push()

                        if (isMasterBranch) {
                            image.push("latest")
                            image.push("candidate")
                        }

                    }
                }
            }
        }

        stage("Warnings") {
            steps {
                warnings consoleParsers: [
                        [parserName: "Java Compiler (javac)"],
                        [parserName: "JavaDoc Tool"]
                ],
                        unstableTotalAll: "0",
                        failedTotalAll: "0"
            }
        }

        stage("PMD") {
            steps {
                step([
                        $class          : 'hudson.plugins.pmd.PmdPublisher',
                        pattern         : '**/target/pmd.xml',
                        unstableTotalAll: "0",
                        failedTotalAll  : "35" //TODO Fix the PMD errors in updateservice to threshold can be set to 0!
                ])
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
    }
}
