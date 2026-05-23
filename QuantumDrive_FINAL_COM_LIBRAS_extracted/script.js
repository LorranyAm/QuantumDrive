const mockUser = {
  email: 'teste@demo.com',
  password: '123456'
};

const emailConfig = {
  publicKey: '',
  serviceId: '',
  cadastroTemplateId: '',
  reservaTemplateId: ''
};

function emailConfigurado() {
  return Boolean(
    emailConfig.publicKey &&
    emailConfig.serviceId &&
    emailConfig.cadastroTemplateId &&
    emailConfig.reservaTemplateId &&
    window.emailjs
  );
}

async function enviarEmail(tipo, dados) {
  if (!emailConfigurado()) {
    console.info('EmailJS ainda não configurado. Confirmação simulada:', tipo, dados);
    return { enviado: false, simulado: true };
  }

  emailjs.init({ publicKey: emailConfig.publicKey });

  const templateId = tipo === 'cadastro'
    ? emailConfig.cadastroTemplateId
    : emailConfig.reservaTemplateId;

  await emailjs.send(emailConfig.serviceId, templateId, dados);
  return { enviado: true, simulado: false };
}

const carros = [
  {
    nome: 'BYD Dolphin Mini',
    imagem: 'https://commons.wikimedia.org/wiki/Special:Redirect/file/BYD_DOLPHIN_MINI_(BRAZIL)_BYD_SEAGULL.jpg',
    desc: 'Compacto 100% elétrico, econômico e ideal para uso urbano, trajetos curtos e estacionamento fácil.',
    autonomia: 'até 280 km',
    perfil: 'Cidade / Econômico',
    preco: 'R$ 159/dia'
  },
  {
    nome: 'BYD Dolphin',
    imagem: 'https://commons.wikimedia.org/wiki/Special:Redirect/file/2021_BYD_Dolphin_EV_(front).jpg',
    desc: 'Hatch elétrico moderno, confortável e prático para casal, trabalho e viagens curtas.',
    autonomia: 'até 291 km',
    perfil: 'Confortável',
    preco: 'R$ 189/dia'
  },
  {
    nome: 'Volvo EX30',
    imagem: 'https://media-downloads.volvocars.com/14919fa0-a885-4a45-9464-b3840084b167/353329_1_5.jpg',
    desc: 'SUV compacto premium com ótimo acabamento, tecnologia embarcada e condução suave.',
    autonomia: 'até 338 km',
    perfil: 'Premium / Família',
    preco: 'R$ 269/dia'
  },
  {
    nome: 'Tesla Model 3',
    imagem: 'https://commons.wikimedia.org/wiki/Special:Redirect/file/Tesla_Model_3_parked,_front_driver_side.jpg',
    desc: 'Sedan elétrico esportivo, indicado para quem procura performance, tecnologia e sofisticação.',
    autonomia: 'até 491 km',
    perfil: 'Esportivo',
    preco: 'R$ 329/dia'
  }
];

const screens = {
  home: document.getElementById('homeScreen'),
  auth: document.getElementById('authScreen'),
  dashboard: document.getElementById('dashboardScreen')
};

const loginPanel = document.getElementById('loginPanel');
const signupPanel = document.getElementById('signupPanel');
const loginForm = document.getElementById('loginForm');
const signupForm = document.getElementById('signupForm');
const recoverPassword = document.getElementById('recoverPassword');
const loginError = document.getElementById('loginError');
const recoverMessage = document.getElementById('recoverMessage');
const signupMessage = document.getElementById('signupMessage');
const gridCarros = document.getElementById('gridCarros');
const pagamento = document.getElementById('pagamento');
const cartaoCampos = document.getElementById('cartaoCampos');
const cartaoInputs = cartaoCampos.querySelectorAll('input');

function criarCard(carro, extra = '') {
  return `
    <article class="card">
      <img src="${carro.imagem}" alt="Imagem real do ${carro.nome}">
      <div class="card-body">
        <h3>${extra}${carro.nome}</h3>
        <p>${carro.desc}</p>
        <div class="specs">
          <div class="spec"><strong>Autonomia</strong>${carro.autonomia}</div>
          <div class="spec"><strong>Perfil</strong>${carro.perfil}</div>
        </div>
        <div class="price">${carro.preco}</div>
        <button class="btn primary" type="button" data-scroll="#reserva" data-carro="${carro.nome}">Reservar este carro</button>
      </div>
    </article>
  `;
}

function mostrarTela(nome) {
  Object.values(screens).forEach((screen) => {
    screen.hidden = true;
    screen.classList.remove('fade-in');
  });

  screens[nome].hidden = false;
  screens[nome].classList.add('fade-in');
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function mostrarAutenticacao(tipo) {
  loginPanel.hidden = tipo !== 'login';
  signupPanel.hidden = tipo !== 'signup';
  loginError.textContent = '';
  recoverMessage.textContent = '';
  signupMessage.textContent = '';
  mostrarTela('auth');
}

gridCarros.innerHTML = carros.map((carro) => criarCard(carro)).join('');

function atualizarCamposCartao() {
  const usaCartao = pagamento.value === 'Cartão de crédito' || pagamento.value === 'Cartão de débito';
  cartaoCampos.hidden = !usaCartao;
  cartaoInputs.forEach((input) => {
    input.required = usaCartao;
    if (!usaCartao) input.value = '';
  });
}

pagamento.addEventListener('change', atualizarCamposCartao);
atualizarCamposCartao();

document.addEventListener('click', (event) => {
  const authButton = event.target.closest('[data-auth]');
  const screenButton = event.target.closest('[data-screen]');
  const scrollButton = event.target.closest('[data-scroll]');
  const accessButton = event.target.closest('[data-action]');

  if (authButton) {
    mostrarAutenticacao(authButton.dataset.auth);
  }

  if (screenButton) {
    mostrarTela(screenButton.dataset.screen);
  }

  if (scrollButton) {
    const target = document.querySelector(scrollButton.dataset.scroll);
    const carroEscolhido = scrollButton.dataset.carro;

    if (carroEscolhido) {
      document.getElementById('veiculoReserva').value = carroEscolhido;
    }

    if (target) {
      mostrarTela('home');
      setTimeout(() => target.scrollIntoView({ behavior: 'smooth' }), 50);
    }
  }

  if (accessButton) {
    const action = accessButton.dataset.action;
    if (action === 'daltonico') document.body.classList.toggle('daltonico');
    if (action === 'contraste') document.body.classList.toggle('contraste');
    if (action === 'fonteMais') document.body.style.fontSize = '18px';
    if (action === 'fonteMenos') document.body.style.fontSize = '15px';
  }
});

loginForm.addEventListener('submit', (event) => {
  event.preventDefault();

  const email = document.getElementById('loginEmail').value.trim();
  const password = document.getElementById('loginPassword').value;

  if (email === mockUser.email && password === mockUser.password) {
    loginError.textContent = '';
    mostrarTela('dashboard');
    return;
  }

  loginError.textContent = 'E-mail ou senha incorretos. Use teste@demo.com e senha 123456.';
});

recoverPassword.addEventListener('click', () => {
  const email = document.getElementById('loginEmail').value.trim() || 'seu e-mail cadastrado';
  recoverMessage.textContent = `Recuperação simulada enviada para ${email}.`;
});

signupForm.addEventListener('submit', async (event) => {
  event.preventDefault();

  const nome = document.getElementById('signupName').value.trim();
  const email = document.getElementById('signupEmail').value.trim();

  const resultadoEmail = await enviarEmail('cadastro', {
    to_name: nome,
    to_email: email,
    subject: 'Confirmação de cadastro - Quantum Drive',
    message: `Olá, ${nome}! Seu cadastro na Quantum Drive foi realizado com sucesso.`
  });

  signupMessage.textContent = resultadoEmail.enviado
    ? `Cadastro criado. Enviamos a confirmação para ${email}.`
    : `Cadastro criado. A confirmação por e-mail está preparada para ${email}.`;

  setTimeout(() => {
    mostrarTela('dashboard');
  }, 1100);
});

document.getElementById('quizForm').addEventListener('submit', (event) => {
  event.preventDefault();

  const uso = document.getElementById('uso').value;
  const prioridade = document.getElementById('prioridade').value;
  let recomendado = carros[0];

  if (uso === 'familia' || prioridade === 'conforto') recomendado = carros[2];
  if (uso === 'viagem' || prioridade === 'autonomia') recomendado = carros[2];
  if (uso === 'esportivo' || prioridade === 'performance') recomendado = carros[3];
  if (uso === 'cidade' || prioridade === 'economia') recomendado = carros[0];

  document.getElementById('quizResultado').innerHTML = criarCard(recomendado, 'Recomendado: ');
});

document.getElementById('reservaForm').addEventListener('submit', async (event) => {
  event.preventDefault();

  const email = document.getElementById('emailReserva').value.trim();
  const nome = document.getElementById('nomeReserva').value.trim();
  const veiculo = document.getElementById('veiculoReserva').value;
  const dataRetirada = document.getElementById('dataRetirada').value;
  const horaRetirada = document.getElementById('horaRetirada').value;
  const dataDevolucao = document.getElementById('dataDevolucao').value;
  const horaDevolucao = document.getElementById('horaDevolucao').value;
  const formaPagamento = document.getElementById('pagamento').value;
  const confirmation = document.getElementById('confirmacao');
  const confirmationText = document.getElementById('confirmacaoTexto');
  const receipt = document.getElementById('reciboReserva');
  const protocolo = `QD-${Date.now().toString().slice(-6)}`;
  const resultadoEmail = await enviarEmail('reserva', {
    to_name: nome,
    to_email: email,
    subject: 'Confirmação de reserva - Quantum Drive',
    protocolo,
    veiculo,
    pagamento: formaPagamento,
    retirada: `${dataRetirada} às ${horaRetirada}`,
    devolucao: `${dataDevolucao} às ${horaDevolucao}`,
    message: `Sua reserva do ${veiculo} foi confirmada. Protocolo: ${protocolo}.`
  });

  confirmation.hidden = false;
  confirmationText.textContent = resultadoEmail.enviado
    ? `Sua reserva foi registrada com sucesso. Enviamos a confirmação para ${email}.`
    : `Sua reserva foi registrada com sucesso. A confirmação por e-mail está preparada para ${email}.`;

  receipt.innerHTML = `
    <h3>Recibo da reserva</h3>
    <div class="receipt-grid">
      <div><span>Protocolo</span><strong>${protocolo}</strong></div>
      <div><span>Cliente</span><strong>${nome}</strong></div>
      <div><span>Veículo</span><strong>${veiculo}</strong></div>
      <div><span>Pagamento</span><strong>${formaPagamento}</strong></div>
      <div><span>Retirada</span><strong>${dataRetirada} às ${horaRetirada}</strong></div>
      <div><span>Devolução</span><strong>${dataDevolucao} às ${horaDevolucao}</strong></div>
    </div>
  `;

  confirmation.scrollIntoView({ behavior: 'smooth' });
});
