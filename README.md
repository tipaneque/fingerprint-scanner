# Fingerprint Scanner API - Sistema de Captura Biométrica

Sistema completo em Java/Spring Boot para interagir com scanner de impressão digital via DLLs nativas, com interface web em tempo real.

## Requisitos

- **Java 17+** (JDK)
- **Maven 3.8+**
- **Windows OS** (as DLLs são Windows-specific)
- **Scanner de Impressão Digital** compatível com as DLLs fornecidas
- **Navegador moderno** (Chrome, Firefox, Edge)

## Estrutura do Projeto

```
fingerprint-scanner/
├── src/
│   ├── main/
│   │   ├── java/com/fingerprint/scanner/
│   │   │   ├── FingerprintScannerApplication.java
│   │   │   ├── config/
│   │   │   │   └── WebSocketConfig.java
│   │   │   ├── controller/
│   │   │   │   └── FingerprintController.java
│   │   │   ├── service/
│   │   │   │   └── FingerprintScannerService.java
│   │   │   └── native_interface/
│   │   │       └── FingerprintDeviceInterface.java
│   │   └── resources/
│   │       ├── application.properties
│   │       └── static/
│   │           └── index.html
│   └── test/
├── lib/
│   ├── FpSplit.dll          ← Separação de dedos
│   ├── GALSXXYY.dll         ← Captura de imagem
│   ├── Gamc.dll             ← Processamento
│   ├── ZAZ_FpStdLib.dll     ← Templates biométricos
│   └── Fione.dll            ← Detecção de fake
├── pom.xml
└── README.md
```

## Instalação Rápida

### 1. Clone ou crie o projeto

```bash
mkdir fingerprint-scanner
cd fingerprint-scanner
```

### 2. Copie as DLLs

Crie a pasta `lib/` na raiz do projeto e copie todas as 5 DLLs:
```
fingerprint-scanner/lib/
├── FpSplit.dll
├── GALSXXYY.dll
├── Gamc.dll
├── ZAZ_FpStdLib.dll
└── Fione.dll
```

### 3. Configure o projeto

Crie o arquivo `pom.xml` com as dependências Maven fornecidas.

### 4. Compile e execute

```bash
mvn clean install
mvn spring-boot:run
```

### 5. Acesse a interface

Abra o navegador em: **http://localhost:8080**

## Configuração das DLLs

### Método 1: Via application.properties (Recomendado)

```properties
jna.library.path=./lib
```

### Método 2: Via variável de sistema

```bash
# Windows CMD
set PATH=%PATH%;C:\caminho\do\projeto\lib

# Windows PowerShell
$env:PATH += ";C:\caminho\do\projeto\lib"
```

### Método 3: Via argumento JVM

```bash
mvn spring-boot:run -Djna.library.path=./lib
```

## API Endpoints

### Gerenciamento do Dispositivo

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | `/api/fingerprint/device/open` | Conecta ao scanner |
| POST | `/api/fingerprint/device/close` | Desconecta do scanner |
| GET | `/api/fingerprint/device/status` | Status da conexão |
| POST | `/api/fingerprint/device/finger-type` | Configura tipo (normal/dry/wet) |
| POST | `/api/fingerprint/device/beep` | Emite beep |

### Captura de Impressões

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | `/api/fingerprint/capture/single` | Captura um dedo |
| POST | `/api/fingerprint/capture/start` | Inicia preview contínuo |
| POST | `/api/fingerprint/capture/stop` | Para preview |
| POST | `/api/fingerprint/capture/multiple` | Captura múltiplos dedos |

### Templates Biométricos

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | `/api/fingerprint/template/create` | Gera templates ISO |
| POST | `/api/fingerprint/template/compare` | Compara dois templates |

### Detecção de Fake

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | `/api/fingerprint/liveness/check` | Verifica autenticidade (LFD) |

## WebSocket

### Conexão
```javascript
const socket = new SockJS('http://localhost:8080/ws-fingerprint');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function(frame) {
    stompClient.subscribe('/topic/fingerprint', function(message) {
        const data = JSON.parse(message.body);
        // data.image - imagem em base64
        // data.quality - qualidade (0-100)
        // data.width, data.height - dimensões
    });
});
```

## Exemplos de Uso

### Conectar ao Dispositivo

```bash
curl -X POST http://localhost:8080/api/fingerprint/device/open
```

Resposta:
```json
{
  "success": true,
  "message": "Dispositivo conectado com sucesso"
}
```

### Capturar Imagem Única

```bash
curl -X POST http://localhost:8080/api/fingerprint/capture/single
```

Resposta:
```json
{
  "success": true,
  "image": "data:image/bmp;base64,Qk1...",
  "quality": 85,
  "width": 300,
  "height": 400
}
```

### Capturar 4 Dedos

```bash
curl -X POST "http://localhost:8080/api/fingerprint/capture/multiple?expectedFingers=4"
```

Resposta:
```json
{
  "success": true,
  "count": 4,
  "quality": 78,
  "fingers": [
    {
      "image": "data:image/bmp;base64,...",
      "quality": 85,
      "angle": 0,
      "x": 100,
      "y": 200
    },
    // ... mais 3 dedos
  ]
}
```

### Comparar Templates

```bash
curl -X POST http://localhost:8080/api/fingerprint/template/compare \
  -H "Content-Type: application/json" \
  -d '{
    "template1": "base64_template_1",
    "template2": "base64_template_2"
  }'
```

Resposta:
```json
{
  "success": true,
  "score": 87,
  "match": true,
  "message": "Impressões digitais correspondem (score: 87)"
}
```

## Interface Web

A interface web oferece:

- Conexão/desconexão do dispositivo
- Preview em tempo real via WebSocket
- Captura de dedo único
- Captura de 4 dedos (esquerda)
- Captura de 2 polegares
- Visualização de qualidade
- Detecção de fake (LFD)
- Geração de templates ISO
- Configuração de tipo de dedo (seco/normal/úmido)

## Troubleshooting

### Erro: "Can't load library"

**Causa:** JNA não encontra as DLLs

**Solução:**
1. Verifique se as DLLs estão em `lib/`
2. Execute: `mvn spring-boot:run -Djna.library.path=./lib`
3. Adicione `lib/` ao PATH do Windows
4. Execute como Administrador

### Erro: "Device not found"

**Causa:** Scanner não está conectado ou drivers ausentes

**Solução:**
1. Verifique conexão USB
2. Instale drivers do fabricante
3. Teste com software original do scanner
4. Execute como Administrador

### Erro: WebSocket não conecta

**Causa:** CORS ou firewall

**Solução:**
1. Verifique `@CrossOrigin` nos controllers
2. Desative firewall temporariamente
3. Use `http://` ao invés de `file://`

### Performance lenta

**Causa:** Resolução alta ou hardware limitado

**Solução:**
1. Reduza resolução: `width=800, height=750`
2. Aumente intervalo: `Thread.sleep(200)`
3. Use captura única ao invés de contínua

### Erro: OutOfMemoryError

**Causa:** Muitas capturas em memória

**Solução:**
```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx2048m"
```

## Segurança

⚠️ **Esta aplicação é para uso interno/desenvolvimento**

Para produção, adicione:
- Autenticação (Spring Security)
- HTTPS (certificado SSL)
- Rate limiting
- Validação de entrada
- Logs de auditoria
- Criptografia de templates

## Parâmetros de Qualidade

| Qualidade | Faixa | Descrição |
|-----------|-------|-----------|
| Excelente | 70-100 | Pronto para uso |
| Boa | 50-69 | Aceitável |
| Regular | 30-49 | Tentar novamente |
| Ruim | 0-29 | Rejeitado |

**Threshold de comparação:** 45 pontos

**Threshold de fake (LFD):** 120 pontos

## Notas Importantes

1. **Compatibilidade:** DLLs são específicas para Windows
2. **Drivers:** Instale drivers do fabricante antes de usar
3. **Permissões:** Pode requerer privilégios de Administrador
4. **Memória:** Monitore uso com capturas contínuas
5. **Thread Safety:** O serviço não é thread-safe para múltiplas capturas simultâneas

## Suporte

Para problemas com as DLLs específicas, consulte a documentação do fabricante do scanner.

---

**Versão:** 1.0.0  
**Última atualização:** 2025
"# fingerprint" 
