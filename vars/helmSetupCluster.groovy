def call(Map config = [:]) {

    /*
     * ------------------------------------------------------------------
     * helmSetupCluster - Shared Library para configurar cluster
     * ------------------------------------------------------------------
     *
     * Parámetros esperados:
     *
     * dir              (obligatorio) → Directorio base donde vive el chart Helm
     * chartDir         (obligatorio) → Carpeta del chart (ej: jekins)
     * kubeconfigCred   (obligatorio) → credentialId tipo "Secret file"
     * sfsId            (obligatorio) → ID del SFS
     * sfs_turbo_shared_path (obligatorio) → Ruta compartida del SFS
     * enterpriseProjectId (obligatorio) → ID del proyecto empresarial
     * releaseName      (opcional)    → Nombre del release Helm
     * namespace        (opcional)    → Namespace Kubernetes default: jekins
     */

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
                  --set elb.elbId=${config.elbId} \\
                  --set persistence.sfsId=${config.sfsId} \\
                  --set persistence.sfsTurboSharedPath=${config.sfs_turbo_shared_path} \\
                  --set enterpriseProjectId=${config.enterpriseProjectId}

                """
            }
        }
    }
}
