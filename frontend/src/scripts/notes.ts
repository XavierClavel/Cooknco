import apiClient from '@/scripts/axios';

export {
  getNotes,
  createNotes,
  updateNotes,
  deleteNotes,
}

async function getNotes(id) {
  return await apiClient.get(`/recipe-notes/${id}`)
}

async function createNotes(id, text) {
  return await apiClient.post(`/recipe-notes/${id}`, text)
}

async function updateNotes(id, text) {
  return await apiClient.put(`/recipe-notes/${id}`, text)
}

async function deleteNotes(id) {
  return await apiClient.delete(`/recipe-notes/${id}`)
}
