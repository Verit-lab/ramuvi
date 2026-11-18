import type { Metadata } from "next";
import LegalDocument, { type LegalSection } from "@/components/legal/LegalDocument";

export const metadata: Metadata = {
  title: "Política de Privacidade",
  description: "Minuta da Política de Privacidade e tratamento de dados da Ramuvi.",
  robots: { index: false, follow: false },
};

const sections: LegalSection[] = [
  {
    title: "1. Quem é responsável pelos dados",
    paragraphs: [
      "A identificação do controlador, do operador técnico e de eventual instituição educacional contratante depende do arranjo efetivo de prestação do serviço. Essas responsabilidades devem ser formalizadas e publicadas antes da aprovação desta política.",
      "PENDÊNCIA DE PUBLICAÇÃO: identificação legal do controlador e canal de contato para solicitações de privacidade.",
    ],
  },
  {
    title: "2. Categorias de dados tratados",
    items: [
      "Dados cadastrais e de matrícula: nome, vínculo com turma, identificação de aluno e, quando informado, e-mail.",
      "Dados de participação e aprendizagem: jornadas e capítulos acessados, atividades, entregas, respostas, avaliações, progresso, XP e conquistas.",
      "Dados de autenticação e segurança: identificadores de conta externa (como emissor e identificador do provedor OIDC), registros de sessão, tokens protegidos e eventos técnicos necessários à segurança.",
      "Dados associados à operação de aulas ao vivo: participação, respostas e registros dos recursos pedagógicos efetivamente utilizados.",
      "Dados locais no navegador: preferências, informações de acesso por dispositivo e arquivos de funcionamento da aplicação, conforme os recursos habilitados.",
    ],
  },
  {
    title: "3. Finalidades do tratamento",
    items: [
      "Identificar usuários autorizados e administrar o vínculo entre estudante, professor e turma.",
      "Disponibilizar jornadas, atividades, conteúdos e aulas ao vivo, registrando o progresso e resultados pedagógicos.",
      "Permitir acompanhamento e correção pelo professor, emissão de convites e controles de acesso.",
      "Manter a segurança, prevenir abuso, investigar falhas, realizar backups e assegurar a continuidade do serviço.",
    ],
  },
  {
    title: "4. Bases legais e contexto educacional",
    paragraphs: [
      "As bases legais devem ser determinadas para cada finalidade e para o papel efetivo do controlador, conforme a Lei Geral de Proteção de Dados Pessoais. Não presumimos que o consentimento seja a base de todos os tratamentos.",
      "Dados de crianças e adolescentes exigem consideração primordial do melhor interesse e observância das normas específicas de proteção em ambientes digitais.",
      "A plataforma não é destinada à coleta de dados pessoais sensíveis sem necessidade pedagógica definida e proteção apropriada; recomenda-se não inserir esses dados em campos livres.",
    ],
  },
  {
    title: "5. Compartilhamento e provedores externos",
    paragraphs: [
      "Professores e gestores autorizados poderão acessar informações das turmas sob sua responsabilidade, conforme as permissões do sistema e as regras institucionais.",
      "A infraestrutura de hospedagem, ferramentas de integração e provedores de identidade, como Google ou Microsoft, quando habilitados, poderão tratar dados necessários à prestação de seus serviços. Os fornecedores e eventuais transferências internacionais deverão ser identificados no inventário de tratamento antes da aprovação final.",
    ],
  },
  {
    title: "6. Cookies, armazenamento local e segurança",
    paragraphs: [
      "A aplicação utiliza mecanismos técnicos de sessão para autenticação e proteção do acesso, além de armazenamento local do navegador para preferências e recursos de estudo. Tokens de autenticação não devem ser publicados ou compartilhados.",
      "Podem existir dados temporários em cache e recursos de aplicação web progressiva, conforme a versão e a configuração utilizadas. A limpeza de dados do navegador pode remover preferências e acessos lembrados no dispositivo.",
      "O uso de ferramentas analíticas, publicidade, cookies opcionais ou processamento adicional exige inventário e divulgação próprios antes da ativação. Esta minuta não presume a existência desses recursos.",
    ],
  },
  {
    title: "7. Retenção, correção e eliminação",
    paragraphs: [
      "Os prazos de retenção por categoria, as necessidades de preservação de registros pedagógicos, logs, backups e as obrigações legais ainda precisam ser definidos pelo responsável. Não é possível prometer exclusão imediata de todas as cópias ou fixar prazo sem essa decisão.",
      "A plataforma possui mecanismos administrativos de anonimização de dados de estudantes em determinados fluxos. O alcance desses mecanismos deve ser conferido, especialmente para backups, histórico pedagógico e integrações.",
    ],
  },
  {
    title: "8. Direitos dos titulares",
    paragraphs: [
      "Conforme a legislação aplicável, o titular pode solicitar confirmação do tratamento, acesso, correção, informação sobre compartilhamento e outras medidas cabíveis, incluindo eliminação ou oposição nas hipóteses legais. Cada pedido poderá exigir validação de identidade e análise de obrigações de retenção.",
      "PENDÊNCIA DE PUBLICAÇÃO: indicar o canal oficial, responsável pelo atendimento e procedimento de resposta. Até sua definição, o usuário deverá buscar o administrador da instância ou a instituição responsável pela turma.",
    ],
  },
  {
    title: "9. Alterações desta política",
    paragraphs: [
      "Novas finalidades, fornecedores ou categorias de dados relevantes devem motivar revisão deste documento e comunicação apropriada aos titulares. A data de vigência e a versão final serão publicadas após a homologação jurídica e operacional.",
    ],
  },
];

export default function PrivacyPage() {
  return <LegalDocument title="Política de Privacidade" intro="Como os dados podem ser tratados nas atividades educacionais da Ramuvi e quais pontos ainda exigem definição formal." sections={sections} />;
}
