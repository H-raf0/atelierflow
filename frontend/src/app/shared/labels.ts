import { EquipmentResponse } from '../api/models/equipment-response';
import { UserSummary } from '../api/models/user-summary';
import { WorkOrderSummary } from '../api/models/work-order-summary';

export type WorkOrderStatus = WorkOrderSummary['status'];
export type Priority = WorkOrderSummary['priority'];
export type WorkOrderType = WorkOrderSummary['type'];
export type Criticality = EquipmentResponse['criticality'];
export type EquipmentStatus = EquipmentResponse['status'];

/** French labels of the API enums (the API speaks English codes, the UI speaks the user's language). */
export const STATUS_LABELS: Record<WorkOrderStatus, string> = {
  OPEN: 'Ouvert',
  PLANNED: 'Planifié',
  IN_PROGRESS: 'En cours',
  DONE: 'Terminé',
  CANCELLED: 'Annulé',
};

export const PRIORITY_LABELS: Record<Priority, string> = {
  LOW: 'Basse',
  MEDIUM: 'Moyenne',
  HIGH: 'Haute',
  URGENT: 'Urgente',
};

export const TYPE_LABELS: Record<WorkOrderType, string> = {
  CORRECTIVE: 'Correctif',
  PREVENTIVE: 'Préventif',
};

export const CRITICALITY_LABELS: Record<Criticality, string> = {
  LOW: 'Faible',
  MEDIUM: 'Moyenne',
  HIGH: 'Élevée',
  CRITICAL: 'Critique',
};

export const EQUIPMENT_STATUS_LABELS: Record<EquipmentStatus, string> = {
  OPERATIONAL: 'En service',
  DOWN: 'En panne',
  IN_MAINTENANCE: 'En maintenance',
};

export const ROLE_LABELS: Record<UserSummary['role'], string> = {
  ADMIN: 'Administrateur',
  MANAGER: 'Responsable',
  TECHNICIAN: 'Technicien',
};

/** Fields of the work order history (audit trail). */
export const HISTORY_FIELD_LABELS: Record<string, string> = {
  status: 'Statut',
  priority: 'Priorité',
  title: 'Titre',
  description: 'Description',
  assignee: 'Technicien',
  scheduledFor: 'Date prévue',
  timeSpentMinutes: 'Temps passé (min)',
  resolution: 'Résolution',
  cancellationReason: "Motif d'annulation",
};

export const PRIORITIES: Priority[] = ['URGENT', 'HIGH', 'MEDIUM', 'LOW'];
export const CRITICALITIES: Criticality[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
export const EQUIPMENT_STATUSES: EquipmentStatus[] = ['OPERATIONAL', 'DOWN', 'IN_MAINTENANCE'];

/** Renders a history value: enum codes are translated, other values shown as they are. */
export function historyValueLabel(field: string, value: string | null): string {
  if (value === null) {
    return '—';
  }
  if (field === 'status') {
    return STATUS_LABELS[value as WorkOrderStatus] ?? value;
  }
  if (field === 'priority') {
    return PRIORITY_LABELS[value as Priority] ?? value;
  }
  return value;
}
