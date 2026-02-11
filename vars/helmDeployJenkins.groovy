def call(Map config = [:]) {

    /*
     * ------------------------------------------------------------------
     * helmDeployJenkins - Shared Library para despliegue de Jenkins en Huawei CCE
     * ------------------------------------------------------------------
     *
     * Parámetros esperados:
     *
     * dir              (obligatorio) → Directorio base donde vive el chart Helm
     * chartDir         (obligatorio) → Carpeta del chart (ej: jekins)
     * kubeconfigCred   (obligatorio) → credentialId tipo "Secret file"
     * elbId            (obligatorio) → ID del ELB creado por Terraform
     * elbIp            (obligatorio) → IP pública del ELB
     * sfsId            (obligatorio) → ID del SFS
     * sfs_turbo_shared_path (obligatorio) → Ruta compartida del SFS
     * enterpriseProjectId (obligatorio) → ID del proyecto empresarial
     * releaseName      (opcional)    → Nombre del release Helm default: jenkins
     * namespace        (opcional)    → Namespace Kubernetes default: jekins
     * healthEndpoint   (opcional)    → Endpoint HTTP para health check default: /login
     * timeoutSeconds   (opcional)    → Timeout máximo de espera default: 300
     */


    def releaseName    = config.releaseName    ?: 'jenkins'
    def namespace      = config.namespace      ?: 'jekins'
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
                  --set elb.elbId=${config.elbId} \\
                  --set persistence.sfsId=${config.sfsId} \\
                  --set persistence.sfsTurboSharedPath=${config.sfs_turbo_shared_path} \\
                  --set enterpriseProjectId=${config.enterpriseProjectId}

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
