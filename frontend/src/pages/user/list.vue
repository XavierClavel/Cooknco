<template>
  <section class="sticker sk-pad-lg users">
    <h1 class="sk-title">{{ $t("users") }}</h1>

    <div class="sk-input-wrap users__search">
      <v-icon>mdi-magnify</v-icon>
      <input
        v-model="query"
        type="search"
        class="sk-input"
        :placeholder="$t('search_user')"
        :aria-label="$t('search_user')"
        @input="updateQuery"
      />
    </div>

    <div class="sk-table-scroll">
      <table class="sk-table">
        <thead>
          <tr>
            <th>{{ $t("user") }}</th>
            <th>{{ $t("role") }}</th>
            <th>{{ $t("actions") }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in users" :key="user.username">
            <td>
              <a
                class="users__who"
                :href="`/user/view/?user=${user.id}`"
                @click.prevent="toViewUser(user.id)"
              >
                <span class="sk-avatar">
                  <img :src="getUserIconUrl(user.id, user.version)" alt="" loading="lazy" />
                </span>
                <span class="users__name">{{ user.username }}</span>
              </a>
            </td>
            <td>
              <select
                v-model="user.role"
                class="sk-input users__role"
                :aria-label="`${$t('role')} — ${user.username}`"
                @change="updateRole(user)"
              >
                <option v-for="role in userRoles" :key="role" :value="role">{{ role }}</option>
              </select>
            </td>
            <td>
              <div class="users__actions">
                <button type="button" class="sk-icon-btn sk-icon-btn--coral users__action" :aria-label="$t('edit')">
                  <v-icon>mdi-pencil-outline</v-icon>
                </button>
                <button
                  type="button"
                  class="sk-icon-btn sk-icon-btn--coral users__action"
                  :aria-label="$t('delete')"
                  @click="performDelete(user.username)"
                >
                  <v-icon>mdi-trash-can-outline</v-icon>
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <v-pagination
      v-if="pagesCount > 1"
      v-model="page"
      :length="pagesCount"
      :total-visible="5"
      density="comfortable"
      rounded="lg"
      variant="outlined"
      class="users__pages"
      @update:model-value="updateDisplay"
    ></v-pagination>
  </section>
</template>

<script lang="ts" setup>
import {listUsers, deleteUser, setRole} from "@/scripts/users";
import {getUserIconUrl, toViewUser} from "@/scripts/common";
import {debounce} from "lodash";
const users = ref<{id: number, username: string, role: string, version?: number}[]>([])
const page = ref<number>(1)
const pagesCount = ref<number>(1)
const pageSize = ref(20)
const query = ref("")

const userRoles = [
  "USER",
  "ADMIN",
]

const updateRole = (user) => {
  setRole(user.id, user.role)
}

const performDelete = (username) => {
  deleteUser(username).then(
    function (response) {
      if (response.status == 200) {
        const index = users.value.findIndex((user) => user.username == username)
        users.value.splice(index,1)
      }
    }).catch(function (error) {
    console.log(error);
  }).finally(function () {
    // always executed
  });

};

const updateDisplay = debounce(() => {
  listUsers({query: query.value}, page.value - 1, pageSize.value).then (
    function (response) {
      console.log(response)
      users.value = response.data.items
      pagesCount.value = Math.ceil(response.data.count / pageSize.value)
    }).catch(function (error) {
    console.log(error);
  })
}, 500)

const updateQuery = () => {
  page.value = 1
  updateDisplay()
}


updateDisplay()

</script>


<style scoped>
.users {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.users__search {
  max-width: 340px;
}

.users__who {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--ink);
  font-weight: 700;
  text-decoration: none;
}

.users__who:hover .users__name {
  text-decoration: underline;
}

.users__who:focus-visible {
  outline: 3px solid var(--mustard);
  outline-offset: 2px;
  border-radius: 999px;
}

.users__role {
  width: 120px;
  min-height: 38px;
  padding: 6px 10px;
  font-size: 13px;
}

.users__actions {
  display: inline-flex;
  gap: 6px;
}

.users__action {
  width: 34px;
  height: 34px;
}

.users__action .v-icon {
  font-size: 18px;
}

.users__pages :deep(.v-pagination__list) {
  gap: 6px;
}

.users__pages :deep(.v-btn) {
  background: var(--white);
  box-shadow: none !important;
}

.users__pages :deep(.v-pagination__item--is-active .v-btn) {
  background: var(--ink);
  color: var(--white);
}
</style>
