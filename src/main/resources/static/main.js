        const API_BASE = 'http://localhost:8080/api/fingerprint';
        let stompClient = null;
        let isConnected = false;
        let lastImageData = null; 

        // Elements of DOM
        const btnOpen = document.getElementById('btnOpen');
        const btnCaptureSingle = document.getElementById('btnCaptureSingle');
        const btnCaptureMultipleEsq = document.getElementById('btnCaptureMultipleEsq');
        const btnCaptureMultipleDir = document.getElementById('btnCaptureMultipleDir');
        const btnCaptureThumbs = document.getElementById('btnCaptureThumbs');
        const btnCreateTemplate = document.getElementById('btnCreateTemplate');
        const statusDot = document.getElementById('statusDot');
        const statusText = document.getElementById('statusText');
        const qualityDisplay = document.getElementById('qualityDisplay');
        const mainPreview = document.getElementById('mainPreview');
        const lastCapture = document.getElementById('lastCapture');
        const fingerType = document.getElementById('fingerType');
        const livenessCheck = document.getElementById('livenessCheck');
        const messageArea = document.getElementById('messageArea');
        const btnSaveCapture = document.getElementById('btnSaveCapture');
        const handDisplay = document.getElementById('handDisplay');

        btnCaptureSingle.addEventListener('click', async () => {
            try {
                const response = await fetch(`${API_BASE}/capture/single`, { method: 'POST' });
                const data = await response.json();

                if (data.success) {
                    lastImageData = data.image;
                    lastCapture.classList.remove('empty');
                    lastCapture.innerHTML = `<img src="${data.image}" alt="Captura">`;
                    btnSaveCapture.disabled = false;
                    showMessage(`Captura realizada! Qualidade: ${data.quality}`, 'success');
                } else {
                    showMessage(data.message, 'error');
                }
            } catch (error) {
                showMessage('Erro: ' + error.message, 'error');
            }
        });

        btnSaveCapture.addEventListener('click', () => {
            if (!lastImageData) return;
            const link = document.createElement('a');
            link.href = lastImageData;  
            link.download = `fingerprint_${Date.now()}.bmp`; // saves as bmp
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            showMessage('Imagem salva com sucesso!', 'success');
            btnSaveCapture.disabled = true;
        });

        // Connect WebSocket
        function connectWebSocket() {
        const socket = new SockJS('http://localhost:8080/ws-fingerprint');
        stompClient = Stomp.over(socket);
        stompClient.debug = null;

        stompClient.connect({}, frame => {
            console.log('WebSocket connected via STOMP/SockJS');
            stompClient.subscribe('/topic/fingerprint', message => {
                const data = JSON.parse(message.body);
                updateMainPreview(data);
            });
        }, error => {
            console.error('Error connecting to WebSocket:', error);
            showMessage('WebSocket connetion error.', 'error');
        });
    }

        // Update the main preview
        function updateMainPreview(data) {
            mainPreview.classList.remove('empty');
            mainPreview.innerHTML = `<img src="${data.image}" alt="Preview" style="max-width:95%;height:auto;">`;
            qualityDisplay.textContent = `Qualidade: ${data.quality}`;
        }

        // Show message
        function showMessage(message, type = 'info') {
            const div = document.createElement('div');
            div.className = `message ${type}`;
            div.textContent = message;
            messageArea.innerHTML = '';
            messageArea.appendChild(div);
            setTimeout(() => div.remove(), 5000);
        }

        btnOpen.addEventListener('click', async () => {
            if (btnOpen.textContent.trim() === "Conectar Dispositivo") {
                await handleBtnOpen();
            } else {
                await handleBtnClose();
            }
        });

        // Conectar dispositivo
        async function handleBtnOpen() {
            try {
                const response = await fetch(`${API_BASE}/device/open`, { method: 'POST' });
                const data = await response.json();

                if (data.success) {
                    isConnected = true;
                    statusDot.classList.add('connected');
                    statusText.textContent = 'Conectado';

                    // Alterar texto do botão
                    btnOpen.textContent = "Desconectar";

                    // Habilitar botões de ação
                    btnCaptureSingle.disabled = false;
                    btnCaptureMultipleEsq.disabled = false;
                    btnCaptureMultipleDir.disabled = false;
                    btnCaptureThumbs.disabled = false;
                    btnCreateTemplate.disabled = false;

                    // Conectar WebSocket e iniciar preview
                    connectWebSocket();
                    showMessage(data.message, 'success');
                    handleBtnPreview();
                } else {
                    showMessage(data.message, 'error');
                }
            } catch (error) {
                showMessage('Erro ao conectar: ' + error.message, 'error');
            }
        }

        // Desconectar dispositivo
        async function handleBtnClose() {
            try {
                const response = await fetch(`${API_BASE}/device/close`, { method: 'POST' });
                const data = await response.json();

                if (data.success) {
                    isConnected = false;
                    statusDot.classList.remove('connected');
                    statusText.textContent = 'Desconectado';

                    // Alterar texto do botão
                    btnOpen.textContent = "Conectar Dispositivo";

                    // Desabilitar botões de ação
                    btnCaptureSingle.disabled = true;
                    btnCaptureMultipleEsq.disabled = true;
                    btnCaptureMultipleDir.disabled = true;
                    btnCaptureThumbs.disabled = true;
                    btnCreateTemplate.disabled = true;

                    // Desconectar WebSocket
                    if (stompClient) {
                        stompClient.disconnect(() => console.log('WebSocket desconectado'));
                    }

                    showMessage(data.message, 'success');
                } else {
                    showMessage(data.message, 'error');
                }
            } catch (error) {
                showMessage('Erro ao desconectar: ' + error.message, 'error');
            }
        }

    

        async function handleBtnPreview() {
            try {
                const response = await fetch(`${API_BASE}/capture/start`, {
                    method: 'POST'
                });
                const data = await response.json();
                
                if (data.success) {
                    updateMainPreview(data)
                    showMessage('Preview iniciado', 'success');
                }
            } catch (error) {
                showMessage('Erro: ' + error.message, 'error');
            }
        }

        // Capture single finger
        btnCaptureSingle.addEventListener('click', async () => {
            try {
                const response = await fetch(`${API_BASE}/capture/single`, {
                    method: 'POST'
                });
                const data = await response.json();
                
                if (data.success) {
                    lastCapture.classList.remove('empty');
                    lastCapture.innerHTML = `<img src="${data.image}" alt="Captura">`;
                    showMessage(`Captura realizada! Qualidade: ${data.quality}`, 'success');
                } else {
                    showMessage(data.message, 'error');
                }
            } catch (error) {
                showMessage('Erro: ' + error.message, 'error');
            }
        });

        // Captures multiple fingers
        btnCaptureMultipleEsq.addEventListener('click', async () => {
            try {
                showMessage('Posicione 4 dedos no scanner...', 'info');
                const response = await fetch(`${API_BASE}/capture/multiple?expectedFingers=4`, {
                    method: 'POST'
                });
                const data = await response.json();
                
                if (data.success) {
                    data.fingers.forEach((finger, index) => {
                    const fingerImg = document.getElementById(`finger-e${index}`);
                    const qualityBadge = document.getElementById(`quality-e${index}`);
                    const slot = fingerImg.parentElement;

                    fingerImg.innerHTML = `<img src="${finger.image}" alt="Dedo ${index}">`;
                    console.log("Qualidade: ", finger.quality)
                    qualityBadge.textContent = finger.quality;
                    qualityBadge.className = 'quality-badge ' + 
                        (finger.quality >= 50 ? 'good' : finger.quality >= 30 ? '' : 'bad');

                    slot.classList.add('success');

            if (data.handDetection) {
                handDisplay.textContent = `Mão: ${data.handDetection.handDescription} (${data.handDetection.confidence}%)`;
            }

            showMessage(
                `${data.count} polegar(es) capturado(s) com sucesso! ` +
                    `Mão: ${data.handDetection?.handDescription || 'N/A'}`,
                'success'
            );
                });
                    showMessage(`${data.count} dedos capturados com sucesso!`, 'success');
                } else {
                    showMessage(data.message, 'error');
                }
            } catch (error) {
                showMessage('Erro: ' + error.message, 'error');
            }
        });

        btnCaptureMultipleDir.addEventListener('click', async () => {
            try {
                showMessage('Posicione 4 dedos no scanner...', 'info');
                const response = await fetch(`${API_BASE}/capture/multiple?expectedFingers=4`, {
                    method: 'POST'
                });
                const data = await response.json();
                
                if (data.success) {
                    data.fingers.forEach((finger, index) => {
                    const fingerImg = document.getElementById(`finger-d${3 - index}`);
                    const qualityBadge = document.getElementById(`quality-d${3 - index}`);
                    const slot = fingerImg.parentElement;

                    fingerImg.innerHTML = `<img src="${finger.image}" alt="Dedo ${index}">`;
                    console.log("Qualidade: ", finger.quality)
                    qualityBadge.textContent = finger.quality;
                    qualityBadge.className = 'quality-badge ' + 
                        (finger.quality >= 50 ? 'good' : finger.quality >= 30 ? '' : 'bad');

                    slot.classList.add('success');
                });
                    showMessage(`${data.count} dedos capturados com sucesso!`, 'success');
                } else {
                    showMessage(data.message, 'error');
                }
            } catch (error) {
                showMessage('Erro: ' + error.message, 'error');
            }
        });

        // Change finger type
        fingerType.addEventListener('change', async () => {
            try {
                const response = await fetch(`${API_BASE}/device/finger-type`, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({type: fingerType.value})
                });
                const data = await response.json();
                showMessage(`Tipo de dedo: ${fingerType.value}`, 'info');
                console.log(`Tipo de dedo: ${fingerType.value}`)
            } catch (error) {
                console.error('Erro ao alterar tipo:', error);
            }
        });

        // Create template
        btnCreateTemplate.addEventListener('click', async () => {
            try {
                showMessage('Gerando templates biométricos...', 'info');
                const response = await fetch(`${API_BASE}/template/create`, {
                    method: 'POST'
                });
                const data = await response.json();
                
                if (data.success) {
                    console.log('Templates gerados:', data.templates);
                    showMessage(`${data.count} template(s) gerado(s) com sucesso!`, 'success');
                    
                    // Save templates to localStorage for later comparison
                    localStorage.setItem('fingerprintTemplates', JSON.stringify(data.templates));
                } else {
                    showMessage(data.message, 'error');
                }
            } catch (error) {
                showMessage('Erro: ' + error.message, 'error');
            }
        });

        // Check status periodically
        setInterval(async () => {
            if (isConnected) {
                try {
                    const response = await fetch(`${API_BASE}/device/status`);
                    const data = await response.json();
                    
                    if (!data.isOpen && isConnected) {
                        // Device was desconnected
                        btnClose.click();
                    }
                } catch (error) {
                    console.error('Erro ao verificar status:', error);
                }
            }
        }, 5000);

        btnCaptureThumbs.addEventListener('click', async () => {
    try {
        showMessage('Posicione os 2 polegares no scanner...', 'info');
        const response = await fetch(`${API_BASE}/capture/thumbs`, { method: 'POST' });
        const data = await response.json();

        if (data.success) {
            data.thumbs.forEach((thumb, index) => {
                const slotId = index === 0 ? 'e4' : 'd4';
                const thumbEl = document.getElementById(`finger-${slotId}`);
                const qualityBadge = document.getElementById(`quality-${slotId}`);
                const slot = thumbEl.closest('.finger-slot'); 

                thumbEl.innerHTML = `
                    <img src="${thumb.image}" 
                         alt="Polegar ${index}" 
                         style="width: 100%; height: 140px; object-fit: contain;">
                `;

                qualityBadge.textContent = thumb.quality;
                qualityBadge.className =
                    'quality-badge ' +
                    (thumb.quality >= 50
                        ? 'good'
                        : thumb.quality >= 30
                        ? ''
                        : 'bad');

                slot.classList.add('success');
            });

            // Update handheld display
            if (data.handDetection) {
                handDisplay.textContent = `Mão: ${data.handDetection.handDescription} (${data.handDetection.confidence}%)`;
            }

            showMessage(
                `${data.count} polegar(es) capturado(s) com sucesso! ` +
                    `Mão: ${data.handDetection?.handDescription || 'N/A'}`,
                'success'
            );
        } else {
            showMessage(data.message, 'error');
        }
    } catch (error) {
        showMessage('Erro: ' + error.message, 'error');
    }
});
