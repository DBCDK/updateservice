pipeline {
    agent { label 'devel8' }
    options {
        buildDiscarder(logRotator(numToKeepStr: '20', daysToKeepStr: '20'))
        disableConcurrentBuilds()
        timeout(time: 1, unit: 'HOURS')
        timestamps()
    }
    triggers { pollSCM('H/3 * * * *') }

    environment {
        MAVEN_OPTS="-XX:+TieredCompilation -XX:TieredStopAtLevel=1 -Dorg.slf4j.simpleLogger.showThreadName=true"
        JAVA_OPTS="-XX:-UseSplitVerifier"
        VERSION=readMavenPom().getVersion()
    }

    tools {
        maven 'maven 3.5'
    }

    stages {
        stage('Build') {
            steps {
                script {
                    withMaven( maven: 'maven 3.5', options: [
                            findbugsPublisher(disabled: true),
                            openTasksPublisher(highPriorityTaskIdentifiers: 'todo', ignoreCase: true, lowPriorityTaskIdentifiers: 'review', normalPriorityTaskIdentifiers: 'fixme,fix')
                    ]) {
                        sh "mvn clean"
                        sh "mvn install pmd:pmd findbugs:findbugs javadoc:aggregate -Dmaven.test.failure.ignore=false"
                    }
                }
            }
            post {
                success {
                    pmd canComputeNew: false, defaultEncoding: '', healthy: '', pattern: '**/pmd.xml', unHealthy: ''
                    findbugs canComputeNew: false, defaultEncoding: '', excludePattern: '', healthy: '', includePattern: '', pattern: '', unHealthy: ''
                    archiveArtifacts '**/target/*.war, **/target/*.log'
                    warnings canComputeNew: false, canResolveRelativePaths: false, categoriesPattern: '', consoleParsers: [[parserName: 'Java Compiler (javac)']], defaultEncoding: '', excludePattern: '', healthy: '', includePattern: '', messagesPattern: '', unHealthy: ''
                }
            }
        }

        stage('Docker') {
            steps {
                script {
                    def dockerFiles = findFiles(glob: '**/Dockerfile')

                    for (def f : dockerFiles) {
                        def dirName = f.path.take(f.path.length() - 11)
                        def projectName = f.path.substring(0, f.path.indexOf('/'))

                        dir(dirName) {
                            def imageName = "updateservice-${VERSION}".toLowerCase()
                            def imageLabel = env.BUILD_NUMBER
                            if (env.BRANCH_NAME && !env.BRANCH_NAME ==~ /master|trunk/ ) {
                                println("Using branch_name ${env.BRANCH_NAME}")
                                imageLabel = env.BRANCH_NAME.split(/\//)[-1]
                                imageLabel = imageLabel.toLowerCase()
                            }

                            println("In ${dirName} build ${projectName} as ${imageName}:$imageLabel")
                            def app = docker.build("$imageName:${imageLabel}".toLowerCase(), '--pull --no-cache .')

                            if (currentBuild.resultIsBetterOrEqualTo('SUCCESS')) {
                                docker.withRegistry('https://docker-i.dbc.dk', 'docker') {
                                    app.push()
                                    if( env.BRANCH_NAME ==~ /master|trunk/ ) {
                                        app.push "latest"
                                        app.push "candidate"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
