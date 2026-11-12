<script setup lang="ts">
import {useI18n} from "vue-i18n";

const props = defineProps({
    action: {
      type: Function,
      required: true,
    },
    values: {
      type:[],
      required: true
    },
    selected: {
      type:[],
      required: false,
      default: []
    }
  }
)

const { t } = useI18n();

// Create a local copy of the prop
const selectedItems = ref([]);
selectedItems.value = props.selected

// Watch for external prop changes and update local state
watch(() => props.selected, (newValue) => {
  selectedItems.value = newValue;
});

// Emit updates when the user modifies the value
const emit = defineEmits(['update:selected']);
const updateValue = () => {
  emit('update:selected', selectedItems.value);
};


const selectItem = (index) => {
  if (selectedItems.value.includes(index)) {
    selectedItems.value.splice(selectedItems.value.indexOf(index),1)
  } else {
    selectedItems.value.push(index)
  }
  updateValue()
  props.action()
}
</script>

<template>
  <div class="sk-chip-row">
    <button
      v-for="(item, index) in values"
      :key="index"
      type="button"
      class="sk-chip"
      :class="{'is-active': selectedItems.includes(index)}"
      :aria-pressed="selectedItems.includes(index)"
      @click="selectItem(index)"
    >{{ $t(item.label) }}</button>
  </div>
</template>
