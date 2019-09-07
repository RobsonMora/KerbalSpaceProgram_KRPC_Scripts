import java.io.IOException;

import org.javatuples.Pair;
import org.javatuples.Triplet;

import krpc.client.Connection;
import krpc.client.RPCException;
import krpc.client.Stream;
import krpc.client.StreamException;
import krpc.client.services.SpaceCenter;
import krpc.client.services.SpaceCenter.Flight;
import krpc.client.services.SpaceCenter.Node;
import krpc.client.services.SpaceCenter.ReferenceFrame;
import krpc.client.services.SpaceCenter.Resources;
import krpc.client.services.SpaceCenter.Vessel;
import krpc.client.services.UI;
import krpc.client.services.UI.Button;
import krpc.client.services.UI.Canvas;
import krpc.client.services.UI.InputField;
import krpc.client.services.UI.Panel;
import krpc.client.services.UI.RectTransform;
import krpc.client.services.UI.Text;

public class DecolagemOrbital {

	private Connection conexao;
	private SpaceCenter centroEspacial;
	private Vessel naveAtual;
	private ReferenceFrame pontoRef;
	private Flight vooNave;
	
	public DecolagemOrbital(Connection con, Vessel nav) throws IOException, RPCException, InterruptedException, StreamException {
		conexao = con;
		centroEspacial = SpaceCenter.newInstance(conexao);
		naveAtual = nav; // objeto da nave
		pontoRef = naveAtual.getOrbit().getBody().getReferenceFrame();
		vooNave = naveAtual.flight(pontoRef);

		float altInicioCurva = 350;
		float altFimCurva = 45000;
		float altFinal = 85000;
		double margemUI = 20.0;
		UI telaUsuario = UI.newInstance(conexao);
		Canvas telaItens = telaUsuario.getStockCanvas();
		// Tamanho da tela de jogo em pixels
		Pair<Double, Double> tamanhoTela = telaItens.getRectTransform().getSize();
		// Adicionar um painel para conter os elementos de UI
		Panel painelInfo = telaItens.addPanel(true);

		// Posicionar o painel � esquerda da tela
		RectTransform retangulo = painelInfo.getRectTransform();
		retangulo.setSize(new Pair<Double, Double>(400.0, 100.0));
		retangulo.setPosition(new Pair<Double, Double>((110 - (tamanhoTela.getValue0()) / 3), 150.0));

		// Adicionar um botao ao painel
		Button botaoPainel = painelInfo.addButton("Lan�amento", true);
		botaoPainel.getRectTransform().setPosition(new Pair<Double, Double>(100.0, -margemUI));
		// Adicionar texto mostrando o empuxo
		InputField caixaTexto = painelInfo.addInputField(true);
		Text textoPainel = painelInfo.addText("Digite a Altitude Final: ", true);
		Text textoPainel2 = painelInfo.addText("", false);

		textoPainel.getRectTransform().setSize(new Pair<Double, Double>(400.0, margemUI));
		textoPainel2.getRectTransform().setSize(new Pair<Double, Double>(400.0, margemUI));

		textoPainel.getRectTransform().setPosition(new Pair<Double, Double>(margemUI, margemUI));
		textoPainel2.getRectTransform().setPosition(new Pair<Double, Double>(margemUI, -margemUI));

		textoPainel.setColor(new Triplet<Double, Double, Double>(0.0, 0.0, 0.0));
		textoPainel.setSize(18);

		textoPainel2.setColor(new Triplet<Double, Double, Double>(1.0, 1.0, 1.0));
		textoPainel2.setSize(24);
		caixaTexto.getRectTransform().setPosition(new Pair<Double, Double>(-100.0, -margemUI));
		caixaTexto.setValue(String.valueOf(altFinal));
		// stream para checar se o botao foi clicado
		Stream<Boolean> botaoClicado = conexao.addStream(botaoPainel, "getClicked");

		// Esperar clique do bot�o:
		while (!botaoClicado.get()) {
			boolean numero = true;
			if (caixaTexto.getChanged()) {
				try {
					altFinal = Float.valueOf(caixaTexto.getValue());
				} catch (Exception e) {
					numero = false;
				}
				if (numero && altFinal > 70500.0) {
					textoPainel.setContent("Digite a Altitude Final: ");
					botaoPainel.setVisible(true);
				} else {
					textoPainel.setContent("Precisa ser um n�mero, acima de 70km!");
					botaoPainel.setVisible(false);
				}
			}
		}
		botaoPainel.remove();
		caixaTexto.remove();
		textoPainel2.setVisible(true);

		// Set up streams for telemetry
		centroEspacial.getUT();
		Stream<Double> ut = conexao.addStream(SpaceCenter.class, "getUT");
		ReferenceFrame pontoReferencia = naveAtual.getSurfaceReferenceFrame();
		Flight voo = naveAtual.flight(pontoReferencia);
		Stream<Double> altitude = conexao.addStream(voo, "getMeanAltitude");
		Stream<Double> apoastro = conexao.addStream(naveAtual.getOrbit(), "getApoapsisAltitude");
		Resources recursoNoEstagio = naveAtual.resourcesInDecoupleStage(2, false);
		Stream<Float> srbFuel = conexao.addStream(recursoNoEstagio, "amount", "SolidFuel");

		// Pre-launch setup
		naveAtual.getControl().setSAS(false); // desligar SAS
		naveAtual.getControl().setRCS(false); // desligar RCS
		naveAtual.getControl().setThrottle(1f); // acelerar ao máximo
		naveAtual.getControl().activateNextStage();
		double empuxoTotalLancamento = naveAtual.getAvailableThrust(); // pegar empuxo disponível
		double massaTotalLancamento = naveAtual.getMass();
		float aceleracaoLancamento = (float) (1.5 / (empuxoTotalLancamento / (massaTotalLancamento * 9.81)));
		naveAtual.getControl().setThrottle(aceleracaoLancamento); // ACELERAR COM 1.5 DE TWR

		// Contagem regressiva...
		textoPainel.setContent("Lan�amento em:");
		textoPainel2.setContent("5...");
		Thread.sleep(1000);
		textoPainel2.setContent("4...");
		Thread.sleep(1000);
		textoPainel2.setContent("3...");
		Thread.sleep(1000);
		textoPainel2.setContent("2...");
		Thread.sleep(1000);
		textoPainel2.setContent("1...");
		Thread.sleep(1000);

		// Ativar o primeiro estágio
		// ativa o próximo estágio
		naveAtual.getAutoPilot().engage(); // ativa o piloto auto
		naveAtual.getAutoPilot().targetPitchAndHeading(90, 90); // direção

		textoPainel.setContent("Altitude em Rela��o ao Solo:");
		// Loop principal de subida
		boolean srbsSeparated = false;
		double anguloGiro = 0; // angulo de giro
		textoPainel2.setSize(18);
		while (true) { // loop while sempre funcionando até um break

			textoPainel2.setContent(String.format(String.valueOf(altitude.get())));
			// Giro de Gravidade
			if (altitude.get() > altInicioCurva && altitude.get() < altFimCurva) {
				double incremento = Math.sqrt((altitude.get() - altInicioCurva) / (altFimCurva - altInicioCurva));
				double novoAnguloGiro = incremento * 90.0;
				if (Math.abs(novoAnguloGiro - anguloGiro) > 0.5) {
					anguloGiro = novoAnguloGiro;
					naveAtual.getAutoPilot().targetPitchAndHeading((float) (90 - anguloGiro), 90);

				}
			}
			Thread.sleep(250);

			if (altitude.get() > 20000) {
				naveAtual.getControl().setThrottle(1.0f);
			}

			// Separa tanques de Comb. sólido quando vazios
			/*
			 * if (!srbsSeparated) {
			 * 
			 * if (srbFuel.get() < 1.0) { //checar nível de CS
			 * naveAtual.getControl().activateNextStage(); //se for menor que 0.1 , ativa
			 * pra separar srbsSeparated = true; //muda variável para true, saindo do if
			 * System.out.println("Separa��o de Boosters"); } }
			 */

			// Diminuir aceleração ao chegar perto do apoastro alvo
			if (apoastro.get() > altFinal * 0.8) {
				textoPainel.setContent("Aproximando-se do apoastro alvo");
				break;
			}
		}
		// Desativa motores ao chegar no apoastro
		naveAtual.getControl().setThrottle(0.25f); // mudar aceleração pra 25%
		while (apoastro.get() < altFinal) {
			textoPainel.setContent("Altitude do Apoastro:");
			textoPainel2.setContent(String.valueOf(apoastro.get()));

		}
		textoPainel.setContent("Apoastro alvo alcan�ado");
		naveAtual.getControl().setThrottle(0); // cortar motor
		Thread.sleep(1000);
		// esperar até sair da atmosfera
		textoPainel.setContent("Esperando sair da atmosfera");
		while (altitude.get() < 70500) {
			textoPainel2.setContent(String.format(String.valueOf(altitude.get())));

			// Planejar circularização usando equação vis-viva
			textoPainel.setContent("Planejando queima de circulariza��o");
			double mu = naveAtual.getOrbit().getBody().getGravitationalParameter(); // pegar parametro G do corpo o qual
																					// a nave orbita
			double r = naveAtual.getOrbit().getApoapsis(); // apoastro da orbita
			double a1 = naveAtual.getOrbit().getSemiMajorAxis(); // semieixo da orbita
			double a2 = r;
			double v1 = Math.sqrt(mu * ((2.0 / r) - (1.0 / a1)));
			double v2 = Math.sqrt(mu * ((2.0 / r) - (1.0 / a2)));
			double deltaV = v2 - v1;
			Node node = naveAtual.getControl().addNode(ut.get() + naveAtual.getOrbit().getTimeToApoapsis(),
				(float) deltaV, 0, 0);

			// Calcular tempo de quueima (equação de foguete)
			double empuxoTotal = naveAtual.getAvailableThrust(); // pegar empuxo disponível
			double isp = naveAtual.getSpecificImpulse() * 9.81; // pegar isp e multiplicar à constante grav
			double massaTotal = naveAtual.getMass(); // pegar massa
			double massaSeca = massaTotal / Math.exp(deltaV / isp); // pegar massa seca
			double taxaQueima = empuxoTotal / isp; // taxa de fluxo, empuxo / isp
			double tempoQueima = (massaTotal - massaSeca) / taxaQueima;
			// Orientate ship
			textoPainel2.setContent("Orientando nave para queima de circulariza��o");
			naveAtual.getAutoPilot().setReferenceFrame(node.getReferenceFrame());
			naveAtual.getAutoPilot().setTargetDirection(new Triplet<Double, Double, Double>(0.0, 1.0, 0.0));
			naveAtual.getAutoPilot().wait_();

			// Wait until burn
			textoPainel.setContent("Esperando at� a queima de circulariza��o");
			double burnUt = ut.get() + naveAtual.getOrbit().getTimeToApoapsis() - (tempoQueima / 2.0);
			textoPainel2.setContent("Tempo de queima: " + burnUt);
			double leadTime = 5;
			// Execute burn
			Thread.sleep(3000);
			textoPainel.setContent("Pronto para executar queima");
			Stream<Double> tempoAteApoastro = conexao.addStream(naveAtual.getOrbit(), "getTimeToApoapsis");
			while (tempoAteApoastro.get() - (tempoQueima / 2.0) > 0) {
			}
			textoPainel.setContent("Executando queima");
			textoPainel2.setContent(String.format(String.valueOf(voo.getVelocity().getValue0())));
			naveAtual.getControl().setThrottle(1);
			Thread.sleep((int) ((tempoQueima - 0.1) * 1000));
			textoPainel.setContent("Ajustando...");
			naveAtual.getControl().setThrottle(0.05f);
			Stream<Triplet<Double, Double, Double>> remainingBurn = conexao.addStream(node, "remainingBurnVector",
				node.getReferenceFrame());
			while (remainingBurn.get().getValue1() > 1) {
			}
			naveAtual.getControl().setThrottle(0);
			node.remove();
			textoPainel.setContent("Lan�amento completo.");
			naveAtual.getAutoPilot().disengage();
			
		}
	}
}
