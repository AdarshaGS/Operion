import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { UsersPanel } from "../settings/UsersPanel";

interface MembersPageProps {
	autoOpenInvite?: boolean;
}

/** First-class /members landing page (dashboard onboarding + quick-action target) - same
 * UsersPanel that /settings/users renders, so member data is stored and managed in one
 * place with two entry points rather than a duplicate page. */
export function MembersPage({ autoOpenInvite = false }: MembersPageProps) {
	return (
		<Stack spacing={3}>
			<Typography variant="h4" component="h1">
				Members
			</Typography>
			<UsersPanel autoOpenInvite={autoOpenInvite} />
		</Stack>
	);
}
