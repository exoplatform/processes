<template>
  <div
    :class="textColor">
    <small
      class="mt-n1 float-right">
      {{ value.length }} / {{ maxLength }}
      <v-icon
        :color="iconColor"
        x-small>
        mdi-information
      </v-icon>
    </small>
  </div>
</template>

<script>
export default {
  props: {
    value: {
      type: String,
      default: ''
    },
    maxLength: {
      type: Number,
      default: null
    }
  },
  computed: {
    validLength() {
      return this.value.length <= this.maxLength;
    },
    textColor() {
      return this.validLength ? 'grey--text custom-counter': 'red--text custom-counter';
    },
    iconColor() {
      return this.validLength ? 'green': 'red';
    }
  }
};
</script>
