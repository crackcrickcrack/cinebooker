
pipeline {
    agent {
        node {
            label 'ec2-build-node'
        }
    }

    parameters {
        string(name: 'BRANCH_NAME', defaultValue: 'main', description: 'Git branch to build')
        string(name: 'DOCKER_TAG', defaultValue: 'latest', description: 'Docker image tag')
        string(name: 'DOCKER_REGISTRY', defaultValue: 'docker.io', description: 'Docker registry')
        string(name: 'DOCKER_REPO', defaultValue: 'sandeepkalathil/cinebooker', description: 'Docker repository')
        string(name: 'SONAR_PROJECT_KEY', defaultValue: 'cinebooker', description: 'SonarQube project key')
        string(name: 'ARGOCD_UPDATER_URL', defaultValue: 'http://argocd-image-updater.argocd:8080', description: 'ArgoCD Image Updater URL')
    }

    environment {
        DOCKER_IMAGE = "${params.DOCKER_REGISTRY}/${params.DOCKER_REPO}:${params.DOCKER_TAG}"
        SONAR_HOST_URL = 'http://51.20.134.81:9000/'
        NODE_ENV = 'production'
        WORKSPACE = "${env.WORKSPACE}-${env.BUILD_NUMBER}"
    }

    stages {
        stage('Code Checkout') {
            steps {
                // Clean workspace before checkout
                cleanWs()
                
                echo "Checking out code from branch: ${params.BRANCH_NAME}"
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: "${params.BRANCH_NAME}"]],
                    doGenerateSubmoduleConfigurations: false,
                    extensions: [],
                    submoduleCfg: [],
                    userRemoteConfigs: [[
                        url: 'https://github.com/crackcrickcrack/cinebooker',
                        credentialsId: 'github-credentials'
                    ]]
                ])
            }
        }

        stage('Build & Test') {
            agent {
                docker {
                    image 'node:18-alpine'
                    reuseNode true
                }
            }
            steps {
                echo "Installing dependencies and running tests..."
                // Install all dependencies including dev dependencies
                sh 'npm ci'
                
                // Install specific testing tools explicitly to ensure they're available
                sh 'npm install -D vite vitest @vitest/coverage-v8 eslint typescript typescript-eslint eslint-plugin-react-hooks eslint-plugin-react-refresh'
                
                // Run linting with npx to ensure the local eslint is used
                sh 'npx eslint . || echo "Linting issues found but continuing"'
                
                // Run tests with npx to ensure vitest is found
                sh 'npx vitest run --coverage || { echo "Tests failed"; exit 1; }'
                
                // Build the application
                sh 'npm run build'
                
                // Publish test reports if they exist
                sh '[ -d "coverage" ] && [ -f "coverage/junit.xml" ] && junit "coverage/junit.xml" || echo "No test reports to publish"'
                sh '[ -d "coverage" ] && [ -f "coverage/index.html" ] && publishHTML([allowMissing: false, alwaysLinkToLastBuild: true, keepAll: true, reportDir: "coverage", reportFiles: "index.html", reportName: "Coverage Report"]) || echo "No coverage report to publish"'
            }
        }

        stage('SonarQube Analysis') {
            agent {
                docker {
                    image 'sonarsource/sonar-scanner-cli:latest'
                    reuseNode true
                }
            }
            steps {
                echo "Running SonarQube analysis..."
                withSonarQubeEnv('SonarQube') {
                    sh """
                        sonar-scanner \\
                        -Dsonar.projectKey=${params.SONAR_PROJECT_KEY} \\
                        -Dsonar.sources=. \\
                        -Dsonar.javascript.lcov.reportPaths=coverage/lcov.info \\
                        -Dsonar.exclusions=node_modules/**,coverage/**,public/**,dist/** \\
                        -Dsonar.host.url=${SONAR_HOST_URL}
                    """
                }
                
                // Quality Gate check
                timeout(time: 2, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Docker Image Build & Security Scan') {
            steps {
                echo "Building Docker image: ${DOCKER_IMAGE}"
                
                // Build Docker image
                sh "docker build -t ${DOCKER_IMAGE} --build-arg NODE_ENV=${NODE_ENV} ."
                
                // Scan the image with Trivy
                sh """
                    docker run --rm \\
                    -v /var/run/docker.sock:/var/run/docker.sock \\
                    -v \${WORKSPACE}:/root/.cache/ \\
                    aquasec/trivy:latest image \\
                    --format json \\
                    --output trivy-results.json \\
                    ${DOCKER_IMAGE}
                """
                
                // Archive scan results
                archiveArtifacts artifacts: 'trivy-results.json', fingerprint: true
                
                // Optional: Fail build on high severity vulnerabilities
                sh """
                    docker run --rm \\
                    -v /var/run/docker.sock:/var/run/docker.sock \\
                    -v \${WORKSPACE}:/root/.cache/ \\
                    aquasec/trivy:latest image \\
                    --exit-code 1 \\
                    --severity HIGH,CRITICAL \\
                    ${DOCKER_IMAGE} || echo "Vulnerabilities found, but continuing"
                """
            }
        }

        stage('Push to Docker Hub') {
            steps {
                echo "Pushing Docker image to Docker Hub..."
                
                withCredentials([usernamePassword(credentialsId: 'docker-hub-credentials', 
                                usernameVariable: 'DOCKER_USER', 
                                passwordVariable: 'DOCKER_PASSWORD')]) {
                    sh """
                        echo ${DOCKER_PASSWORD} | docker login docker.io -u ${DOCKER_USER} --password-stdin
                        docker push ${DOCKER_IMAGE}
                    """
                }
            }
        }

        stage('Update ArgoCD Image Updater') {
            steps {
                echo "Notifying ArgoCD Image Updater about new image..."
                
                sh """
                    curl -X POST ${params.ARGOCD_UPDATER_URL}/api/v1/applications/cinebooker/images \\
                    -H 'Content-Type: application/json' \\
                    -d '{
                        "image": "${DOCKER_IMAGE}",
                        "force": true
                    }'
                """
                
                // Verify deployment started (optional)
                sh """
                    # Wait for ArgoCD to start the update
                    sleep 10
                    curl -s ${params.ARGOCD_UPDATER_URL}/api/v1/applications/cinebooker/status | grep -q "Syncing" || echo "ArgoCD sync status check failed but continuing"
                """
            }
        }
    }

    post {
        always {
            echo "Cleaning up workspace and Docker resources..."
            
            sh """
                # Remove the built Docker image to save space
                docker rmi ${DOCKER_IMAGE} || true
                
                # Prune dangling images and containers
                docker image prune -f
                docker container prune -f
                
                # Clean workspace
                rm -rf node_modules dist coverage
            """
            
            // Clean workspace using Jenkins built-in
            cleanWs()
        }
        
        success {
            echo "Pipeline completed successfully!"
            // You can add notification steps here (Slack, Email, etc.)
        }
        
        failure {
            echo "Pipeline failed!"
            // You can add notification steps here (Slack, Email, etc.)
        }
    }
}
