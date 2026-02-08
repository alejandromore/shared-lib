def call(Map config = [:]) {

    /*
     * ------------------------------------------------------------------
     * helmDeployJenkins - Shared Library para despliegue de Jenkins en Huawei CCE
     * ------------------------------------------------------------------
     *
     * Parámetros esperados:
     *
     * dir              (obligatorio) → Directorio base donde vive el chart Helm
     * chartDir         (obligatorio) → Carpeta del chart (ej: jenkins)
     * kubeconfigCred   (obligatorio) → credentialId tipo "Secret file"
     * elbId            (obligatorio) → ID del ELB creado por Terraform
     * elbIp            (obligatorio) → IP pública del ELB
     * releaseName      (opcional)    → Nombre del release Helm default: jenkins
     * namespace        (opcional)    → Namespace Kubernetes default: jenkins
     * healthEndpoint   (opcional)    → Endpoint HTTP para health check default: /login
     * timeoutSeconds   (opcional)    → Timeout máximo de espera de Helm default: 300 segundos
     *
     * Flujo:
     * 1. Valida acceso al cluster CCE
     * 2. Ejecuta helm upgrade --install
     * 3. Espera que el Deployment esté listo (--wait)
     * 4. Realiza health check público vía ELB
     *
     */

    def releaseName    = config.releaseName    ?: 'jenkins'
    def namespace      = config.namespace      ?: 'jenkins'
    def healthEndpoint = config.healthEndpoint ?: '/login'
    def timeoutSeconds = config.timeoutSeconds ?: 300

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
                  --set elb.id=${config.elbId}

                echo ""
                echo "=========================================="
                echo "Iniciando Health Check público Jenkins..."
                echo "ELB IP: ${config.elbIp}"
                echo "Endpoint: ${healthEndpoint}"
                echo "=========================================="

                for i in \$(seq 1 30); do
                  echo "Intento \$i..."
                  if curl -sf http://${config.elbIp}${healthEndpoint} > /dev/null; then
                    echo ""
                    echo "Jenkins disponible y funcionando correctamente."
                    exit 0
                  fi
                  sleep 10
                done

                echo ""
                echo "ERROR: Jenkins no respondió después de múltiples intentos."
                exit 1
                """
            }
        }
    }
}
