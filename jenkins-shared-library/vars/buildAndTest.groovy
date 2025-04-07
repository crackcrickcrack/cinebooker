
#!/usr/bin/env groovy

def call() {
    echo "Installing dependencies and running tests..."
    
    // Install all dependencies including dev dependencies
    sh 'npm ci'
    
    // Install specific testing tools with compatible versions
    // Using vitest@1.6.1 to match what's already installed in the project
    sh 'npm install -D vite vitest@1.6.1 @vitest/coverage-v8@1.6.1 eslint @eslint/js typescript @typescript-eslint/eslint-plugin @typescript-eslint/parser eslint-plugin-react-hooks eslint-plugin-react-refresh'
    
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
