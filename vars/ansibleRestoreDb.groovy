def call(Map config = [:]) {

    /*
     * Parámetros esperados:
     *
     * dir              (obligatorio) → directorio donde vive Ansible
     * dump             (obligatorio) → ruta del dump en OBS (ej: obs://bucket/file.sql)
     * dbHost           (obligatorio) → IP pública o privada del RDS
     * dbPassword       (obligatorio) → password del usuario postgres
     * accessKeyCred    (obligatorio) → credentialId del Access Key de Huawei Cloud
     * secretKeyCred    (obligatorio) → credentialId del Secret Key de Huawei Cloud
     */

    // -----------------------------
    // Validaciones
    // -----------------------------
    if (!config.dir) {
        error "ansibleRestoreDb: 'dir' es obligatorio"
    }

    if (!config.dump) {
        error "ansibleRestoreDb: 'dump' es obligatorio"
    }

    if (!config.dbHost) {
        error "ansibleRestoreDb: 'dbHost' es obligatorio"
    }

    if (!config.dbPassword) {
        error "ansibleRestoreDb: 'dbPassword' es obligatorio"
    }

    if (!config.accessKeyCred) {
        error "ansibleRestoreDb: 'accessKeyCred' es obligatorio"
    }

    if (!config.secretKeyCred) {
        error "ansibleRestoreDb: 'secretKeyCred' es obligatorio"
    }

    ansiColor('xterm') {

        withCredentials([
            string(credentialsId: config.accessKeyCred, variable: 'OBS_ACCESS_KEY'),
            string(credentialsId: config.secretKeyCred, variable: 'OBS_SECRET_KEY')
        ]) {

            dir(config.dir) {

                sh """
                set -eo pipefail
                export HOME=\$(mktemp -d)

                echo "Configurando obsutil..."
                obsutil config \
                  -i "\$OBS_ACCESS_KEY" \
                  -k "\$OBS_SECRET_KEY" \
                  -e obs.${env.REGION}.myhuaweicloud.com

                echo "Descargando dump desde OBS..."
                obsutil cp "${config.dump}" db_dump.sql -f

                echo "Ejecutando playbook de restauración..."
                ansible-playbook -i inventory/hosts playbooks/restore-db.yml \
                  --extra-vars "db_dump_file=db_dump.sql db_host=${config.dbHost} db_password=${config.dbPassword}"

                echo "Restauración finalizada correctamente"
                """
            }
        }
    }
}
