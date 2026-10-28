pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        DB_HOST = "${env.DB_HOST ?: 'postgres'}"
        DB_PORT = "${env.DB_PORT ?: '5432'}"
        DB_NAME = "${env.DB_NAME ?: 'cypher'}"
        DB_USER = "${env.DB_USER ?: 'cypher_user'}"
        DB_PASSWORD = "${env.DB_PASSWORD ?: 'cypher_ci_password'}"
        JWT_SECRET = "${env.JWT_SECRET ?: 'Y2ktb25seS1jeXBoZXItand0LXNlY3JldC10aGF0LWlzLWxvbmcgZW5vdWdoLWZvci1oczI1Ng=='}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Backend tests') {
            steps {
                dir('backend') {
                    sh './mvnw -B -ntp clean verify'
                }
            }
            post {
                always {
                    junit allowEmptyResults: true,
                        testResults: 'backend/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Reporting build') {
            steps {
                dir('reporting') {
                    sh 'dotnet restore'
                    sh 'dotnet build --no-restore --configuration Release'
                }
            }
        }

        stage('Code and configuration checks') {
            steps {
                sh 'python3 -m compileall -q ai'
                sh 'node scripts/check_frontend_js.mjs'
                sh "find monitoring -name '*.json' -print0 | xargs -0 -n1 python3 -m json.tool >/dev/null"
            }
        }
    }

    post {
        success {
            echo 'CYPHER pipeline passed: backend, reporting worker and code checks.'
        }
        always {
            archiveArtifacts artifacts: 'backend/target/*.jar', allowEmptyArchive: true
        }
    }
}
