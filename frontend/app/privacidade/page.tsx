import type { Metadata } from "next";
import LegalDocument, { type LegalSection } from "@/components/legal/LegalDocument";
import { BRAND, LEGAL_ROUTES, LEGAL_STATUS } from "@/lib/brand";

export const metadata: Metadata = {
  title: "Política de Privacidade",
  description: `Como a ${BRAND.name} trata dados pessoais de professores e alunos.`,
  // Kept out of search indexes until the legal gate in lib/brand.ts is closed.
  robots: LEGAL_STATUS.approved ? undefined : { index: false, follow: false },
  alternates: { canonical: LEGAL_ROUTES.privacy },
};

/*
 * Every statement below was checked against the code of this release
 * (docs/audit/2026-11-19-ramuvi-review.md, "Inventário de dados"). Do not add
 * a promise (deadline, automatic deletion, channel) the product or the
 * company does not yet fulfil.
 */
const sections: LegalSection[] = [
  {
    id: "responsavel",
    title: "1. Quem é responsável",
    paragraphs: [
      `A ${BRAND.name} é desenvolvida e operada pela ${BRAND.company} (“${BRAND.publisher}”).`,
      `Quando uma instituição de ensino contrata a ${BRAND.name} para suas turmas, normalmente é ela quem decide por que e como os dados dos alunos são usados (controladora), e a ${BRAND.publisher} trata os dados em nome dela (operadora). Quando a ${BRAND.publisher} oferece a plataforma diretamente a professores ou alunos, ela pode atuar como controladora. O papel de cada parte é definido no contrato de cada instalação e será indicado aqui antes da vigência desta política.`,
    ],
  },
  {
    id: "dados",
    title: "2. Quais dados são tratados",
    items: [
      "Cadastro do aluno feito pelo professor: nome, matrícula (quando informada), e-mail (quando informado pelo professor ou pelo próprio aluno) e turmas em que está matriculado.",
      "Conta verificada do aluno (quando habilitada): o identificador do provedor de identidade e o identificador da conta nesse provedor, a data da verificação e o vínculo com o cadastro do aluno. A Ramuvi não recebe nem guarda a senha da conta Google ou de outro provedor.",
      "Convites individuais: a Ramuvi guarda apenas uma impressão criptográfica (hash) do convite, com datas de emissão, validade, uso e revogação.",
      "Estudo e atividades: jornadas e conteúdos acessados, progresso, respostas, arquivos e textos entregues, prazos, avaliações, critérios, notas e devolutivas do professor.",
      "Aulas ao vivo: nome de exibição informado ao entrar, respostas em quiz, votação, nuvem de palavras e buzzer, participação em sorteios e pontuação da aula.",
      "Participação e reconhecimento: XP, conquistas e posição no ranking da turma.",
      "Professores: usuário de acesso, preferências da interface e o conteúdo que criam (turmas, jornadas, materiais e atividades).",
      "Dados técnicos: cookies de sessão e de proteção contra falsificação de requisição, além de registros técnicos do servidor usados para segurança e diagnóstico.",
    ],
  },
  {
    id: "finalidades",
    title: "3. Para que os dados são usados",
    items: [
      "Identificar quem acessa e garantir que cada pessoa veja apenas as turmas e conteúdos aos quais tem direito.",
      "Oferecer jornadas, atividades e aulas ao vivo, salvar o progresso e permitir que o aluno continue de onde parou.",
      "Permitir que o professor acompanhe a turma, corrija entregas, dê devolutivas e consulte o histórico das aulas.",
      "Proteger a plataforma contra abuso (por exemplo, limitando tentativas de login), investigar falhas e manter cópias de segurança.",
    ],
    paragraphs: [
      "A Ramuvi não vende dados pessoais, não exibe publicidade e não usa ferramentas de análise de comportamento ou de marketing.",
    ],
  },
  {
    id: "bases-legais",
    title: "4. Bases legais",
    paragraphs: [
      "Os dados são tratados com base na Lei Geral de Proteção de Dados Pessoais (Lei nº 13.709/2018). Em geral, as bases aplicáveis são a execução do contrato de prestação do serviço educacional, o legítimo interesse em manter a plataforma segura e, quando a controladora for instituição de ensino, as obrigações e atribuições próprias da atividade educacional. O consentimento só é usado quando for a base adequada e será pedido de forma específica.",
      "Dados de crianças e adolescentes são tratados no seu melhor interesse, limitados ao necessário para a atividade pedagógica, conforme o art. 14 da LGPD.",
    ],
  },
  {
    id: "compartilhamento",
    title: "5. Quem pode ver os dados",
    items: [
      "O professor das turmas às quais o aluno está vinculado vê cadastro, progresso, entregas, avaliações e participação desses alunos.",
      "Na aula ao vivo, a tela de projeção pode mostrar à turma o nome de exibição e a pontuação dos participantes. O professor decide se o ranking aparece e se os nomes ficam anônimos.",
      "Provedores de identidade (como Google), quando o aluno escolhe entrar com eles, confirmam a identidade conforme suas próprias políticas.",
      "Integrações com Google, Microsoft ou Notion, quando o professor as conecta, trocam com esses serviços os dados necessários à função escolhida, como a lista de alunos de uma turma ou a importação de materiais.",
      "Assistente de avaliação por inteligência artificial, quando habilitado na instalação e acionado pelo professor: o enunciado, a rubrica e as respostas da entrega são enviados ao provedor de IA configurado (por padrão, OpenAI) para gerar uma sugestão. O nome do aluno não é incluído no pedido. A decisão final é sempre do professor.",
      `Fornecedores de hospedagem e infraestrutura, que armazenam os dados em nome da ${BRAND.publisher}. A lista de fornecedores e a indicação de eventual transferência internacional serão publicadas aqui.`,
      "Autoridades públicas, quando houver obrigação legal ou ordem judicial.",
    ],
  },
  {
    id: "navegador",
    title: "6. Cookies e dados no seu navegador",
    items: [
      "Cookies essenciais: sessão de acesso (protegido contra leitura por scripts e enviado só por conexão segura) e token de proteção contra falsificação de requisição. Não usamos cookies de publicidade ou de análise.",
      "Armazenamento local do navegador: tema, tamanho do texto, preferência por menos animação, turmas recentes do professor e o acesso lembrado do aluno neste dispositivo.",
      "Aplicativo instalável (PWA): o navegador guarda apenas arquivos da interface para abrir mais rápido. Respostas, tokens e dados das turmas não ficam nesse armazenamento.",
      "Limpar os dados do site no navegador remove essas preferências e o acesso lembrado neste dispositivo.",
    ],
  },
  {
    id: "seguranca",
    title: "7. Segurança",
    paragraphs: [
      "Usamos conexão criptografada (HTTPS), verificação de senha por hash, convites guardados apenas como hash, proteção contra falsificação de requisição, limite de tentativas de login e controle de acesso por turma no servidor. Nenhuma medida elimina todo risco; incidentes relevantes serão comunicados aos titulares e à Autoridade Nacional de Proteção de Dados (ANPD) quando exigido.",
    ],
  },
  {
    id: "retencao",
    title: "8. Por quanto tempo os dados ficam guardados",
    paragraphs: [
      "Os dados ficam guardados enquanto a turma estiver ativa e enquanto forem necessários para as finalidades acima ou para cumprir obrigações legais. O professor pode anonimizar o cadastro de um aluno, o que substitui nome, matrícula e e-mail por dados não identificáveis e mantém apenas o histórico pedagógico sem identificação.",
      "Os prazos por categoria, incluindo cópias de segurança e registros técnicos, ainda estão sendo definidos e serão publicados nesta seção antes da vigência.",
    ],
  },
  {
    id: "direitos",
    title: "9. Seus direitos",
    paragraphs: [
      "Você pode pedir confirmação de que tratamos seus dados, acesso, correção, anonimização, bloqueio ou eliminação de dados desnecessários, informação sobre compartilhamento, revisão de decisões automatizadas e revogação de consentimento, quando ele for a base do tratamento (art. 18 da LGPD).",
      `Se a sua turma pertence a uma instituição de ensino, o pedido pode ser feito à instituição, que é quem decide sobre esses dados. O canal de privacidade da ${BRAND.company} e o nome do encarregado serão publicados nesta seção; até lá, procure o professor da turma ou o responsável pela instalação. Você também pode reclamar à ANPD.`,
    ],
  },
  {
    id: "alteracoes",
    title: "10. Mudanças nesta política",
    paragraphs: [
      "Novas finalidades, fornecedores ou tipos de dados levam à revisão desta política. A versão em vigor sempre mostrará sua data.",
    ],
  },
];

export default function PrivacyPage() {
  return (
    <LegalDocument
      title="Política de Privacidade"
      intro={`Quais dados a ${BRAND.name} trata, para quê, com quem são compartilhados e como exercer seus direitos.`}
      sections={sections}
    />
  );
}
