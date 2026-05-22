const mockUser = {
  email: 'teste@demo.com',
  password: '123456'
};

const carros = [
  {
    nome: 'BYD Dolphin Mini',
    imagem: 'https://images.unsplash.com/photo-1619767886558-efdc259cde1a?auto=format&fit=crop&w=900&q=80',
    desc: 'Compacto 100% elétrico, econômico e ideal para uso urbano, trajetos curtos e estacionamento fácil.',
    autonomia: 'até 280 km',
    perfil: 'Cidade / Econômico',
    preco: 'R$ 159/dia'
  },
  {
    nome: 'BYD Dolphin',
    imagem: 'https://images.unsplash.com/photo-1617788138017-80ad40651399?auto=format&fit=crop&w=900&q=80',
    desc: 'Hatch elétrico moderno, confortável e prático para casal, trabalho e viagens curtas.',
    autonomia: 'até 291 km',
    perfil: 'Confortável',
    preco: 'R$ 189/dia'
  },
  {
    nome: 'Volvo EX30',
    imagem: 'https://images.unsplash.com/photo-1552519507-da3b142c6e3d?auto=format&fit=crop&w=900&q=80',
    desc: 'SUV compacto premium com ótimo acabamento, tecnologia embarcada e condução suave.',
    autonomia: 'até 338 km',
    perfil: 'Premium / Família',
    preco: 'R$ 269/dia'
  },
  {
    nome: 'Tesla Model 3',
    imagem: 'https://images.unsplash.com/photo-1560958089-b8a1929cea89?auto=format&fit=crop&w=900&q=80',
    desc: 'Sedan elétrico esportivo, indicado para quem procura performance, tecnologia e sofisticação.',
    autonomia: 'até 491 km',
    perfil: 'Esportivo',
    preco: 'R$ 329/dia'
  }
];

const loginScreen = document.getElementById('loginScreen');
const appScreen = document.getElementById('appScreen');
const loginForm = document.getElementById('loginForm');
const loginError = document.getElementById('loginError');
const gridCarros = document.getElementById('gridCarros');

function criarCard(carro, extra = '') {
  return `
    <article class="card">
      <img src="${carro.imagem}" alt="Imagem do ${carro.nome}">
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

function mostrarDashboard() {
  loginScreen.classList.add('fade-out');

  setTimeout(() => {
    loginScreen.hidden = true;
    appScreen.hidden = false;
    appScreen.classList.add('fade-in');
  }, 280);
}

function simularEnvioConfirmacao(email) {
  return new Promise((resolve) => {
    setTimeout(() => {
      resolve({
        enviado: true,
        email
      });
    }, 500);
  });
}

loginForm.addEventListener('submit', (event) => {
  event.preventDefault();

  const email = document.getElementById('loginEmail').value.trim();
  const password = document.getElementById('loginPassword').value;

  if (email === mockUser.email && password === mockUser.password) {
    loginError.textContent = '';
    mostrarDashboard();
    return;
  }

  loginError.textContent = 'E-mail ou senha incorretos. Use teste@demo.com e senha 123456.';
});

gridCarros.innerHTML = carros.map((carro) => criarCard(carro)).join('');

document.addEventListener('click', (event) => {
  const scrollButton = event.target.closest('[data-scroll]');
  const accessButton = event.target.closest('[data-action]');

  if (scrollButton) {
    const target = document.querySelector(scrollButton.dataset.scroll);
    target?.scrollIntoView({ behavior: 'smooth' });
  }

  if (accessButton) {
    const action = accessButton.dataset.action;
    if (action === 'daltonico') document.body.classList.toggle('daltonico');
    if (action === 'contraste') document.body.classList.toggle('contraste');
    if (action === 'fonteMais') document.body.style.fontSize = '18px';
    if (action === 'fonteMenos') document.body.style.fontSize = '15px';
  }
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
