def call(Map config = [:]) {

    /*
     * Parámetros esperados:
     *
     * dir              (obligatorio) → directorio donde vive el chart Helm
     * kubeconfigCred   (obligatorio) → credentialId tipo "Secret file" con kubeconfig del CCE
     * elbId            (obligatorio) → ID del ELB creado por Terraform
     * elbIp            (obligatorio) → IP pública del ELB
     * releaseName      (opcional)    → nombre del release Helm (default: app-basic-java)
     * namespace        (opcional)    → namespace Kubernetes (default: app-basic)
     * healthEndpoint   (opcional)    → endpoint para health check (default: /app-java/healthCheck)
     * timeoutSeconds   (opcional)    → tiempo máximo de espera en segundos (default: 200)
     */

    // -----------------------------
    // Valores por defecto
    // -----------------------------
    def releaseName    = config.releaseName    ?: "app-basic-java"
    def namespace      = config.namespace      ?: "app-basic"
    def healthEndpoint = config.healthEndpoint ?: "/app-java/healthCheck"
    def timeoutSeconds = config.timeoutSeconds ?: 200

    // -----------------------------
    // Validaciones
    // -----------------------------
    if (!config.dir) {
        error "helmDeployApp: 'dir' es obligatorio"
    }

    if (!config.kubeconfigCred) {
        error "helmDeployApp: 'kubeconfigCred' es obligatorio"
    }

    if (!config.elbId) {
        error "helmDeployApp: 'elbId' es obligatorio"
    }

    if (!config.elbIp) {
        error "helmDeployApp: 'elbIp' es obligatorio"
    }

    ansiColor('xterm') {

        dir(config.dir) {

            withCredentials([
                file(credentialsId: config.kubeconfigCred, variable: 'KUBECONFIG_FILE')
            ]) {

                sh """
                set -eo pipefail

                export KUBECONFIG=\$KUBECONFIG_FILE

                echo "Verificando acceso al cluster..."
                kubectl get nodes

                echo "Desplegando release Helm: ${releaseName}"
                helm upgrade --install ${releaseName} ./java-app \
                  -n ${namespace} \
                  --create-namespace \
                  --wait \
                  --set elb.id=${config.elbId}

                echo "Iniciando health check contra ELB..."
                ATTEMPTS=\$(( ${timeoutSeconds} / 10 ))
                COUNT=1

                while [ \$COUNT -le \$ATTEMPTS ]; do
                    echo "Health check intento \$COUNT..."

                    if curl -sf http://${config.elbIp}${healthEndpoint}; then
                        echo "✅ Aplicación disponible"
                        exit 0
                    fi

                    sleep 10
                    COUNT=\$((COUNT+1))
                done

                echo "❌ La aplicación no respondió dentro del tiempo esperado"
                exit 1
                """
            }
        }
    }
}
