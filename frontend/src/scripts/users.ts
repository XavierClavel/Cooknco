import apiClient from '@/plugins/axios.js';

export{
  getUser,
  updateUser,
  listUsers,
  getUsersCount,
  deleteUser,
  updatePassword,
  setRole,
}

class UserOverview {
  private readonly id: number
  private readonly version: number
  private readonly username: string
}


async function getUser(id) {
  return await apiClient.get(`/user/${id}`)
}

async function updateUser(user) {
  return await apiClient.put(`/user`, user)
}

/**
 * @param search anything `URLSearchParams` accepts - an object of filters
 *   (`{query: "xavier"}`), or a query string (`"query=xavier"`, `window.location.search`).
 *   NOT a bare search term: `new URLSearchParams("xavier")` reads that as a parameter
 *   *named* "xavier", so the term never reaches `query=` and the server answers with an
 *   unfiltered page. Two call sites got that wrong, which is why this says so.
 */
async function listUsers(search, page, size) {
  const query = new URLSearchParams(search)
  if (page != undefined) query.append('page', page)
  if (size != undefined) query.append('size', size)
  return await apiClient.get(`/user?${query.toString()}`)
}

async function getUsersCount() {
  return await apiClient.get(`/user/count`)
}

async function setRole(id, role) {
  return await apiClient.put(`/user/${id}/role/${role}`)
}

async function deleteUser(id) {
  return await apiClient.delete(`/user/${id}`)
}

async function updatePassword(oldPassword, newPassword) {
  return await apiClient.put("/user/password",
    {
      old: oldPassword,
      new: newPassword
    }
  )
}
