<template>
  <div class="page">
    <div class="panel">
      <div class="panel-head filters">
        <div class="search">
          <ui-icon name="search" :size="15" />
          <input v-model="filters.query" class="input" style="width:250px"
                 :placeholder="$t('admin_search_user_or_mail')" @input="debouncedReload" />
        </div>
        <select v-model="filters.role" class="select" style="width:130px" @change="reset">
          <option :value="null">{{ $t('admin_all_roles') }}</option>
          <option value="USER">USER</option>
          <option value="ADMIN">ADMIN</option>
        </select>
        <select v-model="filters.status" class="select" style="width:150px" @change="reset">
          <option :value="null">{{ $t('admin_all_statuses') }}</option>
          <option value="ACTIVE">{{ $t('admin_status_active') }}</option>
          <option value="UNVERIFIED">{{ $t('admin_status_unverified') }}</option>
          <option value="SUSPENDED">{{ $t('admin_status_suspended') }}</option>
          <option value="BANNED">{{ $t('admin_status_banned') }}</option>
        </select>
        <select v-model="filters.premium" class="select" style="width:150px" @change="reset">
          <option :value="null">{{ $t('admin_all_premium') }}</option>
          <option value="NONE">{{ $t('admin_premium_none') }}</option>
          <option value="UNTIL">{{ $t('admin_premium_until') }}</option>
          <option value="FOREVER">{{ $t('admin_premium_forever') }}</option>
        </select>
        <span class="spacer"></span>
        <button class="btn icon" :title="$t('admin_refresh')" @click="reload"><ui-icon name="refresh" /></button>
      </div>

      <div class="progress" v-if="loading"><i></i></div>

      <div class="table-wrap">
        <table class="grid">
          <thead>
            <tr>
              <th>{{ $t('user') }}</th>
              <th>{{ $t('mail') }}</th>
              <th>{{ $t('status') }}</th>
              <th>{{ $t('role') }}</th>
              <th>{{ $t('admin_premium') }}</th>
              <th class="right">{{ $t('recipes') }}</th>
              <th class="right">{{ $t('admin_reports') }}</th>
              <th>{{ $t('admin_joined') }}</th>
              <th>{{ $t('admin_last_seen') }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in rows" :key="u.id">
              <td>
                <div class="who">
                  <ui-thumb :src="userIconUrl(u.id, u.version)" round
                            :initials="initials(u.username)" :tint="tint(u.username)" />
                  <span class="truncate">{{ u.username }}</span>
                  <!-- Next to the name rather than only in its own column: scanning the list
                       for who pays is the common read, and a column further right is not
                       where the eye already is. The tooltip is on the wrapper, not the svg:
                       `title` on an <svg> is an attribute and shows nothing. -->
                  <span v-if="u.premiumStatus !== 'NONE'" class="premium-mark" :title="premiumTitle(u)">
                    <ui-icon name="star" :size="14" />
                  </span>
                </div>
              </td>
              <td class="muted small">{{ u.mail }}</td>
              <td>
                <span class="badge" :class="statusTone(u.status)" :title="u.moderationNote || ''">
                  <i class="dot"></i>{{ $t(`admin_status_${u.status.toLowerCase()}`) }}
                </span>
                <div v-if="u.suspendedUntil" class="small subtle nowrap">
                  {{ $t('admin_until') }} {{ fmtDate(u.suspendedUntil) }}
                </div>
              </td>
              <td>
                <select class="select sm-select" :value="u.role"
                        :disabled="busyId === u.id"
                        @change="changeRole(u, ($event.target as HTMLSelectElement).value)">
                  <option value="USER">USER</option>
                  <option value="ADMIN">ADMIN</option>
                </select>
              </td>
              <td>
                <template v-if="u.premiumStatus === 'NONE'">
                  <span class="subtle">—</span>
                  <!-- A grant that has run out reads as none, with the day it ended still
                       there: that is what says the account used to pay. -->
                  <div v-if="u.premiumUntil" class="small subtle nowrap">
                    {{ $t('admin_premium_expired_on') }} {{ fmtDate(u.premiumUntil) }}
                  </div>
                </template>
                <template v-else>
                  <span class="badge accent">
                    <ui-icon name="star" :size="11" />{{ $t(`admin_premium_${u.premiumStatus.toLowerCase()}`) }}
                  </span>
                  <div v-if="u.premiumUntil" class="small subtle nowrap">
                    {{ $t('admin_until') }} {{ fmtDate(u.premiumUntil) }}
                  </div>
                </template>
              </td>
              <td class="right tnum">{{ u.recipesCount }}</td>
              <td class="right tnum">
                <span v-if="u.reportsAgainstCount" class="badge warn">{{ u.reportsAgainstCount }}</span>
                <span v-else class="subtle">0</span>
              </td>
              <td class="small muted nowrap">{{ fmtDate(u.joinDate) }}</td>
              <td class="small muted nowrap">{{ fmtAgo(u.lastActivityDate) }}</td>
              <td class="actions">
                <span class="row-actions">
                  <button v-if="!u.isVerified" class="btn icon" :title="$t('admin_force_verify')"
                          @click="run(u, () => verifyUser(u.id))"><ui-icon name="mailCheck" /></button>
                  <button v-if="u.status === 'SUSPENDED' || u.status === 'BANNED'" class="btn icon"
                          :title="$t('admin_reinstate')"
                          @click="run(u, () => reinstateUser(u.id))"><ui-icon name="userCheck" /></button>
                  <template v-else>
                    <button class="btn icon" :title="$t('admin_suspend')" :disabled="u.role === 'ADMIN'"
                            @click="open(u, 'suspend')"><ui-icon name="clock" /></button>
                    <button class="btn icon" :title="$t('admin_ban')" :disabled="u.role === 'ADMIN'"
                            @click="open(u, 'ban')"><ui-icon name="ban" /></button>
                  </template>
                  <button v-if="u.premiumStatus === 'NONE'" class="btn icon" :title="$t('admin_grant_premium')"
                          :disabled="busyId === u.id"
                          @click="open(u, 'premium')"><ui-icon name="star" /></button>
                  <button v-else class="btn icon" :title="$t('admin_revoke_premium')"
                          :disabled="busyId === u.id"
                          @click="open(u, 'unpremium')"><ui-icon name="starOff" /></button>
                  <button class="btn icon danger-hover" :title="$t('delete')" :disabled="u.role === 'ADMIN'"
                          @click="open(u, 'delete')"><ui-icon name="trash" /></button>
                </span>
              </td>
            </tr>
          </tbody>
        </table>
        <ui-empty v-if="!loading && !rows.length" icon="users"
                  :title="$t('admin_no_result')" :hint="$t('admin_no_result_hint')" />
      </div>

      <div class="panel-foot">
        <ui-pager :page="page" :size="size" :total="total" @update:page="p => { page = p; reload() }" />
      </div>
    </div>

    <!-- suspend -->
    <ui-modal v-model="dialog.suspend" :title="`${$t('admin_suspend')} — ${selected?.username ?? ''}`">
      <div class="col" style="gap:12px">
        <label class="field">
          <span>{{ $t('admin_suspension_days') }}</span>
          <input v-model.number="days" class="input" type="number" min="1" max="3650" />
        </label>
        <label class="field">
          <span>{{ $t('admin_reason') }}</span>
          <textarea v-model="reason" class="input" rows="3"></textarea>
        </label>
        <p class="small muted">{{ $t('admin_suspend_hint') }}</p>
      </div>
      <template #actions>
        <button class="btn" @click="dialog.suspend = false">{{ $t('cancel') }}</button>
        <button class="btn primary" @click="confirmSuspend">{{ $t('admin_suspend') }}</button>
      </template>
    </ui-modal>

    <!-- ban -->
    <ui-modal v-model="dialog.ban" :title="`${$t('admin_ban')} — ${selected?.username ?? ''}`">
      <div class="col" style="gap:12px">
        <label class="field">
          <span>{{ $t('admin_reason') }}</span>
          <textarea v-model="reason" class="input" rows="3"></textarea>
        </label>
        <div class="alert warn"><ui-icon name="alert" /><span>{{ $t('admin_ban_hint') }}</span></div>
      </div>
      <template #actions>
        <button class="btn" @click="dialog.ban = false">{{ $t('cancel') }}</button>
        <button class="btn danger" @click="confirmBan">{{ $t('admin_ban') }}</button>
      </template>
    </ui-modal>

    <!-- premium -->
    <ui-modal v-model="dialog.premium" :title="`${$t('admin_grant_premium')} — ${selected?.username ?? ''}`">
      <div class="col" style="gap:12px">
        <label class="field">
          <span>{{ $t('admin_premium_term') }}</span>
          <select v-model="premiumTerm" class="select">
            <option value="UNTIL">{{ $t('admin_premium_until') }}</option>
            <option value="FOREVER">{{ $t('admin_premium_forever') }}</option>
          </select>
        </label>
        <label v-if="premiumTerm === 'UNTIL'" class="field">
          <span>{{ $t('admin_premium_until_date') }}</span>
          <input v-model="premiumUntil" class="input" type="date" :min="today" />
        </label>
        <p class="small muted">{{ $t('admin_premium_hint') }}</p>
      </div>
      <template #actions>
        <button class="btn" @click="dialog.premium = false">{{ $t('cancel') }}</button>
        <button class="btn primary" :disabled="premiumTerm === 'UNTIL' && !premiumUntil"
                @click="confirmPremium">{{ $t('admin_grant_premium') }}</button>
      </template>
    </ui-modal>

    <!-- end premium -->
    <ui-modal v-model="dialog.unpremium" :title="$t('admin_revoke_premium')">
      <div class="col" style="gap:12px">
        <p>{{ $t('admin_revoke_premium_confirm', {username: selected?.username}) }}</p>
      </div>
      <template #actions>
        <button class="btn" @click="dialog.unpremium = false">{{ $t('cancel') }}</button>
        <button class="btn danger" @click="confirmRevokePremium">{{ $t('admin_revoke_premium') }}</button>
      </template>
    </ui-modal>

    <!-- delete -->
    <ui-modal v-model="dialog.delete" :title="$t('admin_delete_account')">
      <div class="col" style="gap:12px">
        <p>{{ $t('admin_delete_account_confirm', {username: selected?.username}) }}</p>
        <div class="alert danger"><ui-icon name="alert" /><span>{{ $t('admin_irreversible') }}</span></div>
      </div>
      <template #actions>
        <button class="btn" @click="dialog.delete = false">{{ $t('cancel') }}</button>
        <button class="btn danger" @click="confirmDelete">{{ $t('delete') }}</button>
      </template>
    </ui-modal>
  </div>
</template>

<script setup lang="ts">
import {onMounted, reactive, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {
  banUser, deleteUser, errorKey, grantPremium, listUsers, reinstateUser, revokePremium, setRole,
  suspendUser, verifyUser,
} from '../lib/api'
import {fmtAgo, fmtDate} from '../lib/format'
import {userIconUrl} from '../lib/images'
import {notifyError, notifyOk} from '../lib/toast'
import UiIcon from '../components/UiIcon.vue'
import UiModal from '../components/UiModal.vue'
import UiPager from '../components/UiPager.vue'
import UiEmpty from '../components/UiEmpty.vue'
import UiThumb from '../components/UiThumb.vue'

const {t, te} = useI18n()

const rows = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(25)
const total = ref(0)
const busyId = ref<number | null>(null)
const filters = reactive<{query: string; role: string | null; status: string | null; premium: string | null}>({
  query: '', role: null, status: null, premium: null,
})

const selected = ref<any>(null)
const dialog = reactive({suspend: false, ban: false, delete: false, premium: false, unpremium: false})
const days = ref(7)
const reason = ref('')
const premiumTerm = ref<'UNTIL' | 'FOREVER'>('UNTIL')
/** `yyyy-mm-dd`, which is what a date input reads and writes. */
const premiumUntil = ref('')
const today = new Date().toISOString().slice(0, 10)

const statusTone = (s: string) =>
  s === 'BANNED' ? 'danger' : s === 'SUSPENDED' ? 'warn' : s === 'UNVERIFIED' ? 'info' : 'ok'

/** What the star next to a name says on hover: which grant, and when it ends. */
const premiumTitle = (u: any) => {
  const term = t(`admin_premium_${u.premiumStatus.toLowerCase()}`)
  return u.premiumStatus === 'UNTIL' ? `${term} — ${t('admin_until')} ${fmtDate(u.premiumUntil)}` : term
}

const initials = (name: string) => (name || '?').slice(0, 2).toUpperCase()
/** Stable per-name tint behind the initials, for accounts with no picture. */
const tint = (name: string) => {
  let h = 0
  for (const ch of name || '') h = (h * 31 + ch.charCodeAt(0)) % 360
  return `hsl(${h} 42% 88%)`
}

function fail(e: any) {
  const key = errorKey(e)
  notifyError(key && te(key) ? t(key) : t('admin_action_failed'))
}

async function reload() {
  loading.value = true
  try {
    const {data} = await listUsers(filters, page.value - 1, size.value)
    rows.value = data.items
    total.value = data.count
  } catch (e) { fail(e) } finally { loading.value = false }
}

let timer: any
const debouncedReload = () => { clearTimeout(timer); timer = setTimeout(reset, 350) }
const reset = () => { page.value = 1; reload() }

async function run(user: any, action: () => Promise<any>) {
  busyId.value = user.id
  try {
    Object.assign(user, (await action()).data)
    notifyOk(t('admin_action_done'))
  } catch (e) { fail(e) } finally { busyId.value = null }
}

async function changeRole(user: any, role: string) {
  busyId.value = user.id
  try {
    Object.assign(user, (await setRole(user.id, role)).data)
    notifyOk(t('admin_action_done'))
  } catch (e) {
    fail(e)
    await reload()   // put the select back to what the server actually has
  } finally { busyId.value = null }
}

function open(user: any, which: 'suspend' | 'ban' | 'delete' | 'premium' | 'unpremium') {
  selected.value = user
  reason.value = ''
  days.value = 7
  premiumTerm.value = 'UNTIL'
  premiumUntil.value = ''
  dialog[which] = true
}

const confirmPremium = () => {
  const u = selected.value
  const term = premiumTerm.value
  const date = premiumUntil.value
  dialog.premium = false
  // End of the chosen day, UTC: an operator picking today means "through today", and
  // midnight would be a grant that expired before they finished reading the dialog.
  const until = term === 'FOREVER' ? null : Math.floor(Date.parse(`${date}T23:59:59Z`) / 1000)
  run(u, () => grantPremium(u.id, until))
}

const confirmRevokePremium = () => {
  const u = selected.value; dialog.unpremium = false
  run(u, () => revokePremium(u.id))
}

const confirmSuspend = () => {
  const u = selected.value; dialog.suspend = false
  run(u, () => suspendUser(u.id, days.value, reason.value))
}
const confirmBan = () => {
  const u = selected.value; dialog.ban = false
  run(u, () => banUser(u.id, reason.value))
}
const confirmDelete = async () => {
  const u = selected.value; dialog.delete = false
  try {
    await deleteUser(u.id)
    notifyOk(t('admin_action_done'))
    await reload()
  } catch (e) { fail(e) }
}

onMounted(reload)
</script>

<style scoped>
.page { max-width: 1400px; }
.filters { gap: 8px; flex-wrap: wrap; }
.who { display: flex; align-items: center; gap: 8px; min-width: 0; }
.sm-select { height: 27px; font-size: 12.5px; width: 92px; }
.premium-mark { display: inline-flex; color: var(--c-accent); flex: none; }
</style>
