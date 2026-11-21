import type { Metadata } from "next";
import LegalDocument, { type LegalSection } from "@/components/legal/LegalDocument";
import { BRAND, LEGAL_ROUTES, LEGAL_STATUS } from "@/lib/brand";

export const metadata: Metadata = {
  title: "Termos de Uso",
  description: `Regras de acesso e uso da ${BRAND.name}, plataforma educacional da ${BRAND.company}.`,
  // Kept out of search indexes until the legal gate in lib/brand.ts is closed.
  robots: LEGAL_STATUS.approved ? undefined : { index: false, follow: false },
  alternates: { canonical: LEGAL_ROUTES.terms },
};

const sections: LegalSection[] = [
  {
    id: "plataforma",
    title: "1. A plataforma e quem a oferece",
    paragraphs: [
      `A ${BRAND.name} é uma plataforma de ensino e aprendizagem desenvolvida e operada pela ${BRAND.company} (“${BRAND.publisher}”). Ela tem dois ambientes: o ${BRAND.teach}, usado por professores para organizar turmas, jornadas, atividades e aulas ao vivo, e o ${BRAND.learn}, usado por alunos para estudar, entregar atividades e participar das aulas.`,
      `Quando a ${BRAND.name} é usada por uma instituição de ensino, as regras acadêmicas (matrícula, frequência, notas, certificação e disciplina) continuam sendo da instituição. Estes Termos tratam do uso da plataforma e não criam essas regras.`,
    ],
  },
  {
    id: "acesso",
    title: "2. Formas de acesso",
    items: [
      "Professores entram no Ramuvi Teach com usuário e senha fornecidos pelo responsável pela instalação.",
      "Para participar de uma aula ao vivo, o aluno usa o código ou o QR mostrado pelo professor. Esse código vale apenas para aquela aula.",
      "Quando o acesso por conta está habilitado, o aluno entra com uma conta verificada de um provedor de identidade (por exemplo, Google) e confirma um convite individual enviado pelo professor para se vincular à turma.",
      "Convites, códigos e senhas são de uso pessoal. Não os compartilhe e encerre a sessão em computadores ou celulares de uso coletivo.",
    ],
  },
  {
    id: "responsabilidades",
    title: "3. Responsabilidades de quem usa",
    items: [
      "Professores: usar os dados dos alunos apenas para fins pedagógicos da turma, comunicar os critérios de avaliação e revisar sugestões automáticas antes de qualquer decisão.",
      "Alunos: enviar respostas e entregas próprias, respeitar colegas nas dinâmicas ao vivo e não tentar acessar turmas ou dados de outras pessoas.",
      "Todos: não publicar conteúdo ilegal, ofensivo ou que viole direitos autorais ou a privacidade de terceiros, e evitar dados pessoais sensíveis em campos de texto livre.",
    ],
  },
  {
    id: "uso-proibido",
    title: "4. Uso não permitido",
    items: [
      "Contornar autenticação, limites de acesso ou o controle de liberação de conteúdos.",
      "Explorar falhas de segurança, automatizar acessos abusivos ou prejudicar a disponibilidade do serviço.",
      `Usar a ${BRAND.name} para enviar material malicioso ou coletar dados de outros usuários.`,
      `Se você encontrar uma falha de segurança, comunique o professor ou o responsável pela instalação sem explorá-la nem divulgá-la publicamente.`,
    ],
  },
  {
    id: "pontuacao",
    title: "5. Pontuação, ranking e conquistas",
    paragraphs: [
      "XP, ranking e conquistas existem para reconhecer participação. Eles não equivalem a nota, aprovação ou certificado, salvo decisão expressa da instituição de ensino.",
      "Na aula ao vivo, o professor decide se o ranking aparece na tela de projeção e se os nomes ficam anônimos.",
    ],
  },
  {
    id: "propriedade-intelectual",
    title: "6. Conteúdo e propriedade intelectual",
    paragraphs: [
      `Materiais, jornadas e atividades criados por professores continuam pertencendo a seus autores ou às instituições, conforme os acordos entre eles. Ao publicá-los na ${BRAND.name}, o autor autoriza a exibição para as turmas que escolher.`,
      `O software, a marca ${BRAND.name} e a identidade visual pertencem à ${BRAND.company}.`,
    ],
  },
  {
    id: "integracoes",
    title: "7. Recursos opcionais e serviços de terceiros",
    paragraphs: [
      "Alguns recursos só funcionam quando habilitados na instalação: login por provedor de identidade, integrações com Google, Microsoft ou Notion e o assistente de avaliação por inteligência artificial. Cada serviço externo tem termos próprios.",
      "O assistente de avaliação só produz sugestões. A nota e a devolutiva final são sempre do professor.",
    ],
  },
  {
    id: "disponibilidade",
    title: "8. Disponibilidade e mudanças",
    paragraphs: [
      `A ${BRAND.name} recebe correções e melhorias com frequência e pode passar por manutenções. A ${BRAND.name} exige conexão com a internet; recursos para estudo sem conexão ainda não estão disponíveis.`,
      "Mudanças relevantes nestes Termos serão comunicadas com antecedência razoável e indicarão a data da nova versão.",
    ],
  },
  {
    id: "suspensao",
    title: "9. Suspensão de acesso",
    paragraphs: [
      "Em caso de uso não permitido ou risco à segurança, o acesso pode ser suspenso de forma proporcional. Convites e sessões podem ser revogados pelo professor ou pelo responsável pela instalação. Quem tiver o acesso suspenso pode pedir revisão ao professor, ao responsável pela instalação ou ao canal de atendimento da seção 11.",
    ],
  },
  {
    id: "privacidade",
    title: "10. Dados pessoais",
    paragraphs: [
      "O tratamento de dados pessoais está descrito na Política de Privacidade, que faz parte destes Termos. O uso por crianças e adolescentes segue o princípio do melhor interesse e as regras da Lei Geral de Proteção de Dados Pessoais (Lei nº 13.709/2018).",
    ],
  },
  {
    id: "contato",
    title: "11. Contato e legislação",
    paragraphs: [
      `Estes Termos são regidos pelas leis brasileiras. O canal oficial de atendimento da ${BRAND.company} será publicado nesta seção; até lá, procure o professor da turma ou o responsável pela instalação que você utiliza.`,
    ],
  },
];

export default function TermsPage() {
  return (
    <LegalDocument
      title="Termos de Uso"
      intro={`Regras para acessar e usar a ${BRAND.name}, oferecida pela ${BRAND.company}.`}
      sections={sections}
    />
  );
}
