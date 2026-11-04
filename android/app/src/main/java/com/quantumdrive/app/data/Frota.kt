package com.quantumdrive.app.data

import com.quantumdrive.app.R

/** Mesma frota do site, com as fotos oficiais da BYD e o consumo da calculadora BYD 2026. */
object Frota {
    val carros = listOf(
        Carro("dolphin-mini", "BYD Dolphin Mini", Categoria.Hatch, R.drawable.byd_dolphin_mini,
            "Compacto 100% elétrico, econômico e ágil. Ideal para cidade, trajetos curtos e estacionar em qualquer vaga.",
            280, 75, 38.0, 4, 230, "30–80% em ~30 min", 6.50, 159, 6,
            itens = listOf("Central multimídia giratória de 10,1\"", "Câmera de ré", "Carregador por indução", "Partida sem chave")),
        Carro("dolphin", "BYD Dolphin", Categoria.Hatch, R.drawable.byd_dolphin,
            "Hatch elétrico moderno e confortável, ótimo para casal, trabalho e viagens curtas.",
            291, 95, 44.9, 5, 345, "30–80% em ~30 min", 6.00, 189, 5,
            itens = listOf("Bateria Blade (LFP)", "Tela giratória de 12,8\"", "Assistente de faixa", "Bomba de calor")),
        Carro("dolphin-plus", "BYD Dolphin Plus", Categoria.Hatch, R.drawable.byd_dolphin_plus,
            "Versão mais completa do Dolphin, com mais desempenho, conforto e autonomia para pegar estrada.",
            427, 204, 60.5, 5, 345, "30–80% em ~29 min", 5.79, 249, 4,
            itens = listOf("204 cv de potência", "Teto panorâmico", "Bancos com ventilação", "Piloto adaptativo")),
        Carro("yuan-pro", "BYD Yuan Pro", Categoria.SUV, R.drawable.byd_yuan_pro,
            "SUV compacto elétrico com posição de dirigir elevada, prático para família pequena e uso diário.",
            250, 177, 45.1, 5, 380, "30–80% em ~30 min", 5.54, 219, 4,
            itens = listOf("Posição de dirigir elevada", "Tela de 12,8\"", "Câmera 360°", "Controle de cruzeiro")),
        Carro("yuan-plus", "BYD Yuan Plus", Categoria.SUV, R.drawable.byd_yuan_plus_awd,
            "SUV médio 100% elétrico, espaçoso e equilibrado: ótimo para família e viagens.",
            420, 204, 60.5, 5, 440, "30–80% em ~29 min", 5.05, 269, 5,
            itens = listOf("Porta-malas de 440 L", "Teto solar panorâmico", "Câmera 360°", "V2L: alimenta aparelhos externos")),
        Carro("seal", "BYD Seal", Categoria.Sedan, R.drawable.byd_seal,
            "Sedan elétrico esportivo premium, para quem procura performance, tecnologia e sofisticação.",
            570, 531, 82.5, 5, 400, "30–80% em ~26 min", 4.51, 329, 3,
            itens = listOf("0–100 km/h em 3,8 s (AWD)", "Tração integral", "Head-up display", "Som premium Dynaudio")),
        Carro("sealion-7", "BYD Sealion 7", Categoria.SUV, R.drawable.byd_sealion_7,
            "SUV cupê elétrico de alto desempenho, com visual esportivo e muito espaço interno.",
            482, 530, 82.5, 5, 520, "10–80% em ~24 min", 4.36, 399, 2,
            itens = listOf("Tração integral de 530 cv", "Teto panorâmico", "Suspensão adaptativa", "Bancos com massagem")),
        Carro("han", "BYD Han", Categoria.Sedan, R.drawable.byd_han,
            "Sedan executivo elétrico de luxo: silencioso, potente e com acabamento refinado.",
            521, 517, 85.4, 5, 410, "30–80% em ~25 min", 6.36, 429, 2,
            itens = listOf("Acabamento em couro Nappa", "0–100 km/h em 3,9 s", "Bancos traseiros aquecidos", "Som Dynaudio")),
        Carro("tan", "BYD Tan EV", Categoria.SUV, R.drawable.byd_tan,
            "SUV grande de 7 lugares, 100% elétrico e com tração integral. Perfeito para família grande.",
            530, 517, 108.8, 7, 235, "30–80% em ~30 min", 3.95, 459, 2,
            itens = listOf("7 lugares", "Tração integral", "Porta-malas de até 1.655 L rebatido", "Suspensão DiSus-C"))
    )

    val destaques = listOf("seal", "sealion-7", "dolphin-plus", "tan")

    val maxAutonomia = carros.maxOf { it.autonomiaKm }

    val lojas = listOf(
        "São Paulo — Paulista", "São Paulo — Aeroporto de Congonhas",
        "Guarulhos — Aeroporto GRU", "Campinas — Cambuí", "Rio de Janeiro — Barra"
    )
    val pagamentos = listOf("Pix", "Cartão de crédito", "Cartão de débito", "Pagamento na loja")
}
