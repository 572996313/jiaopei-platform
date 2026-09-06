import { defineStore } from 'pinia'
import { fetchInstitutions, type InstitutionSummary } from '@/api/organization'
import { useAuthStore } from '@/stores/auth'
import { useEducationScopeStore } from '@/stores/educationScope'

export const useInstitutionStore = defineStore('institution', {
  state: () => ({
    institutions: [] as InstitutionSummary[],
    currentInstitutionId: null as string | null,
    loaded: false,
    loading: false,
  }),

  getters: {
    currentInstitution(): InstitutionSummary | undefined {
      return this.institutions.find((item) => item.id === this.currentInstitutionId)
    },
    currentInstitutionName(): string {
      const inst = this.currentInstitution
      if (!inst) {
        return '未选择机构'
      }
      return inst.orgName || inst.shortName || inst.orgCode || `机构 #${inst.id}`
    },
    isSingleInstitution(): boolean {
      return this.institutions.length === 1
    },
    hasInstitutions(): boolean {
      return this.institutions.length > 0
    },
  },

  actions: {
    async loadInstitutions() {
      if (this.loaded || this.loading) {
        return
      }
      this.loading = true
      try {
        this.institutions = await fetchInstitutions()
        this.loaded = true
        this.autoSelect()
      } finally {
        this.loading = false
      }
    },

    autoSelect() {
      if (this.currentInstitutionId) {
        return
      }
      const auth = useAuthStore()
      const preferred = auth.currentUser?.institutionId
      if (preferred && this.institutions.some((item) => item.id === preferred)) {
        this.currentInstitutionId = preferred
      } else if (this.institutions.length > 0) {
        this.currentInstitutionId = this.institutions[0].id
      }
    },

    setInstitution(id: string | null) {
      if (id === this.currentInstitutionId) {
        return
      }
      this.currentInstitutionId = id
      useEducationScopeStore().reset()
    },

    reset() {
      this.institutions = []
      this.currentInstitutionId = null
      this.loaded = false
      this.loading = false
      useEducationScopeStore().reset()
    },
  },
})
