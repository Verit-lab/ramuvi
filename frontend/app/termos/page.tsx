import type { Metadata } from "next";
import LegalDocument, { type LegalSection } from "@/components/legal/LegalDocument";

export const metadata: Metadata = {
  title: "Termos de Uso",
  description: "Minuta dos Termos de Uso da plataforma educacional Ramuvi.",
  robots: { index: false, follow: false },
};

const sections: LegalSection[] = [
  {
    title: "1. Sobre a plataforma",
    paragraphs: [
      "A Ramuvi é uma plataforma digital de apoio a experiências de ensino e aprendizagem. Oferece ambientes para professores e estudantes, jornadas, atividades, avaliações, conteúdos, aulas ao vivo, participação e acompanhamento pedagógico.",
      "Este documento descreve regras propostas para uso da plataforma. A identificação do responsável contratual e o alcance de cada oferta institucional deverão ser formalizados antes da entrada em vigor.",
    ],
  },
  {
    title: "2. Acesso e responsabilidades",
    items: [
      "O acesso do professor pode exigir credenciais próprias e autorização institucional. Cada usuário deve proteger suas credenciais e encerrar sessões em dispositivos compartilhados.",
      "O aluno pode acessar aulas por código ou, quando habilitado, entrar com identidade verificada e vincular-se a uma turma mediante convite individual.",
      "Códigos de acesso, convites e sessões são pessoais conforme sua finalidade. Não compartilhe credenciais ou convites individuais nem tente acessar turmas e dados de terceiros.",
      "O professor deve utilizar os recursos de acompanhamento e avaliação de acordo com as regras da instituição e informar aos estudantes os critérios pedagógicos aplicáveis.",
    ],
  },
  {
    title: "3. Uso adequado e conteúdo",
    items: [
      "É proibido tentar contornar autenticação ou limites de acesso, explorar vulnerabilidades, distribuir código malicioso ou prejudicar a disponibilidade da plataforma.",
      "Materiais enviados ou publicados devem respeitar direitos autorais, privacidade e direitos de terceiros. Não publique dados pessoais de outras pessoas sem fundamento e autorização apropriados.",
      "Respostas, notas, pontuação, rankings e conquistas são instrumentos de apoio pedagógico e não substituem, por si só, avaliações ou certificações formalmente emitidas pela instituição.",
      "Recursos de terceiros e integrações podem depender de termos próprios; seu uso deve ser informado antes de habilitá-los.",
    ],
  },
  {
    title: "4. Disponibilidade e atualizações",
    paragraphs: [
      "A plataforma poderá receber correções, melhorias e manutenções. A disponibilidade de cada recurso depende da configuração e do estágio de implantação da instância utilizada.",
      "Funcionalidades como acesso offline, login por provedores externos e integrações só podem ser consideradas disponíveis quando efetivamente habilitadas e homologadas.",
      "Mudanças relevantes neste documento deverão ser comunicadas de forma adequada antes da sua aplicação, com indicação da data da nova versão.",
    ],
  },
  {
    title: "5. Dados pessoais e privacidade",
    paragraphs: [
      "O tratamento de dados pessoais, inclusive identidade, matrícula e progresso do aluno, deverá observar a Política de Privacidade e as bases legais aplicáveis. O simples uso destes Termos não substitui consentimento específico quando exigido por lei.",
      "O uso por crianças e adolescentes requer atenção reforçada ao melhor interesse, à proteção integral e às regras específicas aplicáveis ao contexto educacional.",
    ],
  },
  {
    title: "6. Suspensão e contestação",
    paragraphs: [
      "Acesso indevido, abuso ou risco à segurança poderão justificar medidas proporcionais de proteção, sujeitas à análise e aos procedimentos definidos pelo responsável pela operação. A possibilidade de contestação e revisão deve ser assegurada quando pertinente.",
      "As regras de permanência, certificação, vínculo escolar e sanções disciplinares pertencem à instituição educacional responsável e não são criadas por este documento.",
    ],
  },
  {
    title: "7. Responsável e atendimento",
    paragraphs: [
      "PENDÊNCIA DE PUBLICAÇÃO: confirmar a pessoa jurídica ou física responsável, sua identificação legal, endereço ou meio de contato oficial, canal de suporte e canal de privacidade. Não presumir vínculo institucional com terceiros pelo simples uso da plataforma.",
      "Enquanto esta minuta não for aprovada, dúvidas devem ser encaminhadas ao administrador da instância ou ao responsável institucional pela turma, sem que isso substitua o canal formal a ser divulgado.",
    ],
  },
];

export default function TermsPage() {
  return <LegalDocument title="Termos de Uso" intro="Condições propostas de acesso, participação e responsabilidade na Ramuvi." sections={sections} />;
}
