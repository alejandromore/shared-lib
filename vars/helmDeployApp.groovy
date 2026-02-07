def call(Map config = [:]) {

    /*
     * ------------------------------------------------------------------
     * helmDeployApp - Shared Library para despliegue en Huawei CCE
     * ------------------------------------------------------------------
     *
     * Parámetros esperados:
     *
     * dir              (obligatorio) → Directorio base donde vive el chart Helm
     * chartDir         (obligatorio) → Carpeta del chart (ej: java-app)
     * kubeconfigCred   (obligatorio) → credentialId tipo "Secret file"
     * elbId            (obligatorio) → ID del ELB creado por Terraform
     * elbIp            (obligatorio) → IP pública del ELB
     * releaseName      (opcional)    → Nombre del release Helm default: app-basic-java
     * namespace        (opcional)    → Namespace Kubernetes default: app-basic
     * healthEndpoint   (opcional)    → Endpoint HTTP para health check default: /app-java/healthCheck
     * timeoutSeconds   (opcional)    → Timeout máximo de espera de Helm default: 200 segundos

     */

    def releaseName    = config.releaseName    ?: 'app-basic-java'
    def namespace      = config.namespace      ?: 'app-basic'
    def healthEndpoint = config.healthEndpoint ?: '/app-java/healthCheck'
    def timeoutSeconds = config.timeoutSeconds ?: 200

    ansiColor('xterm') {
        dir(config.dir) {

            withCredentials([
                file(credentialsId: config.kubeconfigCred, variable: 'KUBECONFIG')
            ]) {

                sh """
                set -eo pipefail
                export KUBECONFIG=\$KUBECONFIG

                echo "=========================================="
                echo "Validando acceso al cluster CCE..."
                echo "=========================================="
                kubectl get nodes

                echo ""
                echo "=========================================="
                echo "Desplegando Helm Release: ${releaseName}"
                echo "Namespace: ${namespace}"
                echo "Chart: ${config.chartDir}"
                echo "=========================================="

                helm upgrade --install ${releaseName} ./${config.chartDir} \\
                  -n ${namespace} \\
                  --create-namespace \\
                  --wait \\
                  --timeout ${timeoutSeconds}s \\
                  --set elb.id=${config.elbId} \\
                  --set-string app.healthPath=${healthEndpoint}

                echo ""
                echo "=========================================="
                echo "Iniciando Health Check público..."
                echo "ELB IP: ${config.elbIp}"
                echo "Endpoint: ${healthEndpoint}"
                echo "=========================================="

                for i in \$(seq 1 20); do
                  echo "Intento \$i..."
                  if curl -sf http://${config.elbIp}${healthEndpoint}; then
                    echo ""
                    echo "Aplicación disponible y funcionando correctamente."
                    exit 0
                  fi
                  sleep 10
                done

                echo ""
                echo "ERROR: Health check falló después de múltiples intentos."
                exit 1
                """
            }
        }
    }
}
