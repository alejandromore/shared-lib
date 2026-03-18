def call(Map config = [:]) {

    /*
     * Parámetros:
     *
     * dir             (obligatorio)
     * varsFile        (opcional)
     * action          (opcional)
     * outputs         (opcional)
     * project         (opcional) → app-env (ej: huaweidemo-dev)
     * backendSuffix   (opcional) → standard | workload-identity
     */

    if (!config.dir) {
        error "terraformDeploy: 'dir' es obligatorio"
    }

    def tfVarsFile    = config.varsFile ?: 'local.tfvars'
    def action        = config.action   ?: 'deploy'
    def outputs       = config.outputs  ?: []
    def project       = config.project  ?: null
    def backendSuffix = config.backendSuffix ?: null

    ansiColor('xterm') {
        dir(config.dir) {

            // ============================
            // Setear app_env dinámico
            // ============================
            if (project) {
                env.TF_VAR_app_env = project.replace("_", "-")
                echo "Terraform app_env = ${env.TF_VAR_app_env}"
            }

            // ============================
            // Backend config dinámico
            // ============================
            def backendArg = ""

            if (project && backendSuffix) {
                backendArg = "-backend-config='key=${project}-${backendSuffix}.tfstate'"
            } else if (project) {
                backendArg = "-backend-config='key=${project}.tfstate'"
            }

            sh """
              set -e
              rm -rf .terraform
              terraform init ${backendArg}
            """

            if (action == 'deploy') {

                sh "terraform plan -var-file='${tfVarsFile}'"
                sh "terraform apply -auto-approve -var-file='${tfVarsFile}'"

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