import apiClient from '@/plugins/axios.js';

export {
  reportContent,
  reportReasons,
  reportTargetTypes,
}

/**
 * Files a moderation report. The backend rejects reporting your own content and collapses
 * repeat reports on the same target from the same account.
 */
async function reportContent(targetType, targetId, reason, comment) {
  return await apiClient.post(`/report`, {targetType, targetId, reason, comment})
}

const reportReasons = [
  'SPAM',
  'INAPPROPRIATE_CONTENT',
  'HARASSMENT',
  'COPYRIGHT',
  'MISINFORMATION',
  'OTHER',
]

const reportTargetTypes = [
  'RECIPE',
  'USER',
  'COOKBOOK',
]
