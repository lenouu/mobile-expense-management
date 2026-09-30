import { AdminSession, adminRequest, PagedModel } from '@/services/admin-monitoring';

// Shapes of /admin/categories (US09).

export type DefaultCategory = {
  id: number;
  name: string;
  description: string | null;
  icon: string | null;
  color: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

/** Body of POST and PUT. PUT is a full replace: an omitted optional field is cleared. */
export type SaveCategory = {
  name: string;
  description?: string;
  icon?: string;
  color?: string;
};

export function fetchCategories(
  session: AdminSession,
  options: { active?: boolean; page?: number; size?: number } = {},
) {
  const params = new URLSearchParams({
    page: String(options.page ?? 0),
    size: String(options.size ?? 50),
    sort: 'name,asc',
  });
  if (options.active !== undefined) {
    params.set('active', String(options.active));
  }
  return adminRequest<PagedModel<DefaultCategory>>(`/admin/categories?${params}`, session);
}

export function createCategory(session: AdminSession, category: SaveCategory) {
  return adminRequest<DefaultCategory>('/admin/categories', session, { method: 'POST', body: category });
}

export function updateCategory(session: AdminSession, id: number, category: SaveCategory) {
  return adminRequest<DefaultCategory>(`/admin/categories/${id}`, session, { method: 'PUT', body: category });
}

export function setCategoryActive(session: AdminSession, id: number, active: boolean) {
  return adminRequest<DefaultCategory>(`/admin/categories/${id}/status`, session, {
    method: 'PATCH',
    body: { active },
  });
}
