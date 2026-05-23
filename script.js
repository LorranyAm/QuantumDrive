const mockUser = {
  email: 'teste@demo.com',
  password: '123456'
};

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
const loginError = document.getElementById('loginError');
const signupMessage = document.getElementById('signupMessage');
const gridCarros = document.getElementById('gridCarros');

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
        <button class="btn primary" type="button" data-scroll="#reserva">Reservar este carro</button>
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
  signupMessage.textContent = '';
  mostrarTela('auth');
}

function simularEnvioConfirmacao(email) {
  return new Promise((resolve) => {
    setTimeout(() => {
      resolve({ enviado: true, email });
    }, 500);
  });
}

gridCarros.innerHTML = carros.map((carro) => criarCard(carro)).join('');

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

signupForm.addEventListener('submit', (event) => {
  event.preventDefault();
  signupMessage.textContent = 'Cadastro demonstrativo criado. Entrando na sua conta...';

  setTimeout(() => {
    mostrarTela('dashboard');
  }, 700);
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
  const confirmation = document.getElementById('confirmacao');
  const confirmationText = document.getElementById('confirmacaoTexto');
  const result = await simularEnvioConfirmacao(email);

  confirmation.hidden = false;
  confirmationText.textContent = result.enviado
    ? `Sua reserva foi registrada com sucesso. Uma confirmação foi preparada para o e-mail ${result.email}.`
    : 'Sua reserva foi registrada, mas não foi possível preparar a confirmação por e-mail.';
  confirmation.scrollIntoView({ behavior: 'smooth' });
});
