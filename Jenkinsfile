// ============================================================
// 项目A：Java RAG 引擎 —— SSH 远程部署版（配合模拟 Linux 服务器）
// 前置：
//   1. Jenkins Tools 配好 jdk21 / maven3.9
//   2. 装 SSH Agent 插件
//   3. 配 SSH 凭证 ID = deploy-ssh-key（用户 deploy + 私钥）
//   4. 远程机 /opt/rag-engine/docker-compose.yml 已就位
// ============================================================
pipeline {
    agent any

    tools {
        maven 'maven3.9'
        jdk 'jdk21'
    }

    environment {
        // Jenkins 容器推镜像到宿主机 registry，必须走 host.docker.internal
        REGISTRY   = 'host.docker.internal:5000'
        IMAGE      = 'rag-engine'
        // SSH 目标：模拟 Linux 服务器（宿主机映射 2222）
        DEPLOY_HOST = 'host.docker.internal'
        DEPLOY_PORT = '2222'
        DEPLOY_USER = 'deploy'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    GIT_SHA = sh(script: 'git rev-parse --short=7 HEAD', returnStdout: true).trim()
                    env.TAG = "${env.BUILD_NUMBER}-${GIT_SHA}"
                    echo "本次镜像 tag = ${env.TAG}"
                }
            }
        }

        stage('Test') {
            steps {
                dir('rag-engine') {
                    sh 'mvn -B test'
                }
            }
        }

        stage('Build & Push Image (Jib)') {
            steps {
                dir('rag-engine') {
                    sh """
                    mvn -B compile jib:build \
                      -Djib.to.image=${REGISTRY}/${IMAGE} \
                      -Djib.to.tags=${TAG},latest \
                      -Djib.allowInsecureRegistries=true
                    """
                }
            }
        }

        stage('Deploy') {
            steps {
                sshagent(credentials: ['deploy-ssh-key']) {
                    sh """
                    ssh -o StrictHostKeyChecking=no \
                        -o UserKnownHostsFile=/dev/null \
                        -p ${DEPLOY_PORT} ${DEPLOY_USER}@${DEPLOY_HOST} '
                          cd /opt/rag-engine &&
                          export IMAGE_TAG=${TAG} &&
                          sudo -n docker compose pull rag-engine &&
                          sudo -n docker compose up -d rag-engine
                        '
                    """
                }
            }
        }
    }

    post {
        success { echo "✅ 发布成功：localhost:5000/${IMAGE}:${TAG}" }
        failure { echo "❌ 失败，请查看 Console Output" }
    }
}
