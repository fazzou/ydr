build:
    scala-cli package --docker .

publish: build
    docker push ghcr.io/fazzou/ydr:latest

deploy: publish
    #!/usr/bin/env bash
    set -euo pipefail
    
    # Check if environment variables are set
    if [ -z "${REMOTE_HOST:-}" ]; then
        echo "Error: REMOTE_HOST environment variable is not set"
        exit 1
    fi
    
    if [ -z "${REMOTE_PATH:-}" ]; then
        echo "Error: REMOTE_PATH environment variable is not set"
        exit 1
    fi
    
    # Execute commands remotely via SSH
    ssh "${REMOTE_HOST}" "cd ${REMOTE_PATH} && docker compose down && docker compose pull && docker compose up -d"
    
    echo "Deployment completed successfully"

