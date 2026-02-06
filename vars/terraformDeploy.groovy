def call(Map config = [:]) {

    /*
     * Parámetros esperados:
     *
     * dir        (obligatorio) → directorio de Terraform
     * varsFile   (opcional)    → archivo tfvars (default: local.tfvars)
     * action     (opcional)    → deploy | destroy (default: deploy)
     * outputs    (opcional)    → lista de outputs a capturar
     */

    if (!config.dir) {
        error "terraformDeploy: 'dir' es obligatorio"
    }

    def tfVarsFile = config.varsFile ?: 'local.tfvars'
    def action     = config.action   ?: 'deploy'
    def outputs    = config.outputs  ?: []

    ansiColor('xterm') {
        dir(config.dir) {

            sh '''
              set -e
              rm -rf .terraform
              terraform init
            '''

            if (action == 'deploy') {

                sh "terraform plan -var-file='${tfVarsFile}'"
                sh "terraform apply -auto-approve -var-file='${tfVarsFile}'"

                // Captura outputs si fueron definidos
                outputs.each { outputName ->
                    def value = sh(
                        script: "terraform output -raw ${outputName}",
                        returnStdout: true
                    ).trim()

                    env[outputName] = value

                    writeFile(
                        file: "${env.WORKSPACE}/${outputName}.txt",
                        text: value
                    )

                    echo "Terraform output capturado: ${outputName}"
                }

            } else if (action == 'destroy') {

                sh "terraform destroy -auto-approve -var-file='${tfVarsFile}'"

            } else {
                error "terraformDeploy: acción no soportada -> ${action}"
            }
        }
    }
}
