import { HttpErrorResponse } from '@angular/common/http';

/** Body of an API error (RFC 9457 ProblemDetail, with the application's stable `code`). */
interface Problem {
  code?: string;
  detail?: string;
  violations?: { field: string; message: string }[];
}

/** User-facing messages for the business rule codes returned by the API. */
const MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: 'Identifiant ou mot de passe incorrect.',
  INVALID_TRANSITION: "Ce changement de statut n'est pas autorisé pour cet ordre de travail.",
  ACCESS_DENIED: 'Action réservée aux responsables de maintenance.',
  NOT_ASSIGNEE: "Cet ordre de travail n'est pas assigné à vous.",
  PREVENTIVE_RESERVED: 'Les OT préventifs sont créés par les responsables.',
  ASSIGNEE_NOT_TECHNICIAN: 'Seul un technicien peut être assigné.',
  WORK_ORDER_CLOSED: 'Un ordre de travail clôturé ne peut plus être modifié.',
  EQUIPMENT_CODE_TAKEN: 'Ce code machine est déjà utilisé.',
  EQUIPMENT_HAS_HISTORY: 'Cette machine a un historique de maintenance : elle ne peut pas être supprimée.',
  CONCURRENT_MODIFICATION: "Quelqu'un d'autre vient de modifier cet élément. Rechargez la page.",
  INVALID_REFRESH_TOKEN: 'Votre session a expiré, veuillez vous reconnecter.',
};

/** Turns any error raised by an API call into a sentence for a snackbar. */
export function describeError(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) {
    return 'Une erreur inattendue est survenue.';
  }
  if (error.status === 0) {
    return 'Le serveur est injoignable. Vérifiez votre connexion.';
  }
  const problem = (error.error ?? {}) as Problem;
  if (problem.code === 'VALIDATION_FAILED' && problem.violations?.length) {
    return 'Champs invalides : ' + problem.violations.map((v) => `${v.field} (${v.message})`).join(', ');
  }
  return (problem.code && MESSAGES[problem.code]) ?? problem.detail ?? `Erreur ${error.status}.`;
}
