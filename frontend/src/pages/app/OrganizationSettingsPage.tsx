import { useState, type FormEvent } from 'react'
import { Building2, Plus, UserPlus } from 'lucide-react'
import { toast } from 'sonner'
import { ApiError } from '../../api/client'
import {
  useCreateOrganizationMutation,
  useDeleteOrganizationMutation,
  useInviteMemberMutation,
  useOrganizationMembersQuery,
  useOrganizationsQuery,
  useRemoveMemberMutation,
  useUpdateMemberRoleMutation,
  type MembershipRole,
  type OrganizationMember,
} from '../../api/organizations'
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '../../components/ui/alert-dialog'
import { Button } from '../../components/ui/button'
import { HoldToConfirmButton } from '../../components/ui/hold-to-confirm-button'
import { Input } from '../../components/ui/input'
import { Label } from '../../components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../../components/ui/select'
import { Skeleton } from '../../components/ui/skeleton'
import { useCanManage, useIsOwner } from '../../features/organizations/useCanManage'
import { RetentionSettings } from '../../features/retention/RetentionSettings'
import { useAuthStore } from '../../store/authStore'

const ROLE_OPTIONS: MembershipRole[] = ['OWNER', 'ADMIN', 'VIEWER']

export default function OrganizationSettingsPage() {
  const currentOrganizationId = useAuthStore((state) => state.currentOrganizationId)
  const currentRole = useAuthStore((state) => state.currentRole)
  const currentUserId = useAuthStore((state) => state.user?.id ?? null)
  const setActiveOrganization = useAuthStore((state) => state.setActiveOrganization)
  const canManage = useCanManage()
  const isOwner = useIsOwner()

  const { data: organizations = [] } = useOrganizationsQuery()
  const activeOrganization = organizations.find((organization) => organization.id === currentOrganizationId) ?? null

  const membersQuery = useOrganizationMembersQuery(currentOrganizationId)
  const members = membersQuery.data ?? []

  const createMutation = useCreateOrganizationMutation()
  const inviteMutation = useInviteMemberMutation(currentOrganizationId ?? 0)
  const updateRoleMutation = useUpdateMemberRoleMutation(currentOrganizationId ?? 0)
  const removeMemberMutation = useRemoveMemberMutation(currentOrganizationId ?? 0)
  const deleteOrganizationMutation = useDeleteOrganizationMutation(currentOrganizationId ?? 0)

  const [newOrganizationName, setNewOrganizationName] = useState('')
  const [inviteEmail, setInviteEmail] = useState('')
  const [removeTarget, setRemoveTarget] = useState<OrganizationMember | null>(null)
  const [deleteOpen, setDeleteOpen] = useState(false)

  const handleCreateOrganization = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const name = newOrganizationName.trim()
    if (!name) {
      toast.error('Organization name is required.')
      return
    }
    if (name.length > 100) {
      toast.error('Organization name must be at most 100 characters.')
      return
    }
    try {
      const created = await createMutation.mutateAsync(name)
      setActiveOrganization(created.id, created.role)
      setNewOrganizationName('')
      toast.success('Organization created.')
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Could not create the organization.')
    }
  }

  const handleInvite = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const email = inviteEmail.trim()
    if (!email) {
      toast.error('Email is required.')
      return
    }
    try {
      await inviteMutation.mutateAsync(email)
      setInviteEmail('')
      toast.success('Member invited as viewer.')
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Could not invite the member.')
    }
  }

  const handleRoleChange = async (member: OrganizationMember, role: MembershipRole) => {
    if (role === member.role) {
      return
    }
    try {
      await updateRoleMutation.mutateAsync({ userId: member.userId, role })
      toast.success('Member role updated.')
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Could not update the member role.')
    }
  }

  const handleRemove = async () => {
    if (!removeTarget) {
      return
    }
    try {
      await removeMemberMutation.mutateAsync(removeTarget.userId)
      toast.success('Member removed.')
      setRemoveTarget(null)
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Could not remove the member.')
    }
  }

  const handleDeleteOrganization = async () => {
    try {
      await deleteOrganizationMutation.mutateAsync()
      setDeleteOpen(false)
      toast.success('Organization deleted.')
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Could not delete the organization.')
    }
  }

  const canEditMember = (member: OrganizationMember): boolean => {
    if (!canManage) {
      return false
    }
    return isOwner || member.role !== 'OWNER'
  }

  return (
    <section className="space-y-6">
      <header className="space-y-2">
        <p className="section-tag">Organization</p>
        <h1 className="font-display text-2xl font-semibold text-slate-100">Organization &amp; access</h1>
        <p className="text-sm text-slate-400">
          Switch tenants, manage members and control who can change telemetry configuration.
        </p>
      </header>

      <div className="rounded-2xl border border-white/10 bg-panel/60 p-6">
        <header className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 className="flex items-center gap-2 font-display text-lg font-semibold text-slate-100">
              <Building2 className="h-5 w-5 text-cyan-300" aria-hidden="true" />
              {activeOrganization?.name ?? 'No active organization'}
            </h2>
            <p className="mt-1 text-sm text-slate-400">
              {currentRole ? (
                <>
                  Your role: <span className="text-slate-200">{currentRole}</span>
                </>
              ) : (
                'Select an organization in the header switcher.'
              )}
            </p>
          </div>
          {isOwner && activeOrganization ? (
            <Button type="button" variant="outline" onClick={() => setDeleteOpen(true)}>
              Delete organization
            </Button>
          ) : null}
        </header>
        {!canManage ? (
          <p className="mt-4 rounded-lg border border-white/10 bg-white/[0.02] px-3 py-2 text-sm text-slate-400">
            You have read-only access. Ask an owner or admin to change applications, dashboards, alerts or health
            checks.
          </p>
        ) : null}
      </div>

      <div className="rounded-2xl border border-white/10 bg-panel/60 p-6">
        <header>
          <h2 className="font-display text-lg font-semibold text-slate-100">Create organization</h2>
          <p className="mt-1 text-sm text-slate-400">
            You become the owner of a new organization and can invite members to it.
          </p>
        </header>
        <form className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-end" onSubmit={handleCreateOrganization}>
          <div className="flex-1 space-y-1.5">
            <Label htmlFor="organization-name">Name</Label>
            <Input
              id="organization-name"
              value={newOrganizationName}
              maxLength={100}
              placeholder="Acme production"
              onChange={(event) => setNewOrganizationName(event.target.value)}
            />
          </div>
          <Button type="submit" disabled={createMutation.isPending}>
            <Plus className="h-4 w-4" aria-hidden="true" />
            {createMutation.isPending ? 'Creating…' : 'Create organization'}
          </Button>
        </form>
      </div>

      <div className="rounded-2xl border border-white/10 bg-panel/60 p-6">
        <header>
          <h2 className="font-display text-lg font-semibold text-slate-100">Members</h2>
          <p className="mt-1 text-sm text-slate-400">
            Owners manage access and deletion. Admins can invite members and change resource configuration. Viewers
            have read-only access.
          </p>
        </header>

        {canManage && activeOrganization ? (
          <form className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-end" onSubmit={handleInvite}>
            <div className="flex-1 space-y-1.5">
              <Label htmlFor="invite-email">Invite by email</Label>
              <Input
                id="invite-email"
                type="email"
                autoComplete="email"
                value={inviteEmail}
                placeholder="teammate@example.com"
                onChange={(event) => setInviteEmail(event.target.value)}
              />
            </div>
            <Button type="submit" variant="secondary" disabled={inviteMutation.isPending}>
              <UserPlus className="h-4 w-4" aria-hidden="true" />
              {inviteMutation.isPending ? 'Inviting…' : 'Invite viewer'}
            </Button>
          </form>
        ) : null}

        {membersQuery.isLoading ? (
          <div className="mt-6 space-y-3" aria-busy="true">
            {[0, 1, 2].map((row) => (
              <Skeleton key={row} className="h-12 w-full" />
            ))}
          </div>
        ) : membersQuery.isError ? (
          <div role="alert" className="mt-6 rounded-xl border border-red-500/30 bg-red-500/5 p-4">
            <p className="text-sm text-red-200">
              {membersQuery.error instanceof ApiError
                ? membersQuery.error.message
                : 'Could not load organization members.'}
            </p>
            <Button
              type="button"
              variant="outline"
              className="mt-3"
              onClick={() => void membersQuery.refetch()}
              disabled={membersQuery.isFetching}
            >
              {membersQuery.isFetching ? 'Retrying…' : 'Retry'}
            </Button>
          </div>
        ) : members.length === 0 ? (
          <p className="mt-6 rounded-xl border border-dashed border-white/10 p-6 text-center text-sm text-slate-400">
            This organization has no members.
          </p>
        ) : (
          <div className="mt-6 overflow-x-auto">
            <table className="w-full min-w-[36rem] border-collapse text-left text-sm">
              <thead>
                <tr className="border-b border-white/10 text-xs uppercase tracking-wide text-slate-500">
                  <th scope="col" className="py-2 pr-4 font-medium">
                    Member
                  </th>
                  <th scope="col" className="py-2 pr-4 font-medium">
                    Role
                  </th>
                  <th scope="col" className="py-2 text-right font-medium">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody>
                {members.map((member) => {
                  const editable = canEditMember(member)
                  const removable = canManage && member.userId !== currentUserId
                    && (isOwner || member.role !== 'OWNER')
                  return (
                    <tr key={member.membershipId} className="border-b border-white/5 last:border-0">
                      <td className="py-3 pr-4">
                        <p className="font-medium text-slate-200">
                          {member.username ?? `User ${member.userId}`}
                          {member.userId === currentUserId ? (
                            <span className="ml-2 text-xs text-slate-500">(you)</span>
                          ) : null}
                        </p>
                        {member.email ? <p className="text-xs text-slate-500">{member.email}</p> : null}
                      </td>
                      <td className="py-3 pr-4">
                        {editable ? (
                          <Select
                            value={member.role}
                            onValueChange={(value) => void handleRoleChange(member, value as MembershipRole)}
                          >
                            <SelectTrigger aria-label={`Role for ${member.username ?? member.userId}`} className="h-9 w-36">
                              <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                              {ROLE_OPTIONS.filter((role) => isOwner || role !== 'OWNER').map((role) => (
                                <SelectItem key={role} value={role}>
                                  {role}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        ) : (
                          <span className="text-slate-300">{member.role}</span>
                        )}
                      </td>
                      <td className="py-3 text-right">
                        {removable ? (
                          <Button
                            type="button"
                            variant="ghost"
                            className="text-red-300 hover:text-red-200"
                            onClick={() => setRemoveTarget(member)}
                          >
                            Remove
                          </Button>
                        ) : null}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <RetentionSettings />

      <AlertDialog open={removeTarget !== null} onOpenChange={(open) => !open && setRemoveTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Remove member</AlertDialogTitle>
            <AlertDialogDescription>
              Remove <span className="text-slate-200">{removeTarget?.username ?? 'this member'}</span> from the
              organization? They will immediately lose access to its data.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={removeMemberMutation.isPending}>Cancel</AlertDialogCancel>
            <Button
              type="button"
              variant="outline"
              className="border-red-500/40 bg-red-500/10 text-red-200 hover:bg-red-500/20 hover:text-red-100"
              disabled={removeMemberMutation.isPending}
              onClick={() => void handleRemove()}
            >
              {removeMemberMutation.isPending ? 'Removing…' : 'Remove member'}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete organization</AlertDialogTitle>
            <AlertDialogDescription>
              Deleting <span className="text-slate-200">{activeOrganization?.name}</span> removes its memberships.
              Telemetry and configuration are no longer reachable. This action cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleteOrganizationMutation.isPending}>Cancel</AlertDialogCancel>
            <HoldToConfirmButton
              variant="outline"
              disabled={deleteOrganizationMutation.isPending}
              className="border-red-500/40 bg-red-500/10 text-red-200 hover:bg-red-500/20 hover:text-red-100"
              onConfirm={() => {
                void handleDeleteOrganization()
              }}
            >
              {deleteOrganizationMutation.isPending ? 'Deleting…' : 'Delete organization'}
            </HoldToConfirmButton>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </section>
  )
}
