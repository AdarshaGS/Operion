import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { listExternalServices, updateExternalServiceProperties, type ExternalServiceSettingsResponse } from "../../api/externalServices";
import { ApiError } from "../../api/client";
import { useAuth } from "../../auth/AuthContext";

function formatPropertyLabel(key: string): string {
	const segment = key.includes(".") ? key.split(".")[1] : key;
	return segment
		.split("-")
		.map((word) => word.charAt(0).toUpperCase() + word.slice(1))
		.join(" ");
}

interface ServiceCardProps {
	service: ExternalServiceSettingsResponse;
	canEdit: boolean;
	onSaved: (updated: ExternalServiceSettingsResponse) => void;
}

function ServiceCard({ service, canEdit, onSaved }: ServiceCardProps) {
	const [values, setValues] = useState<Record<string, string>>(() =>
		Object.fromEntries(service.properties.map((property) => [property.key, ""])),
	);
	const [touchedSecrets, setTouchedSecrets] = useState<Set<string>>(new Set());
	const [saving, setSaving] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [saved, setSaved] = useState(false);

	function handleChange(key: string, value: string) {
		setValues((prev) => ({ ...prev, [key]: value }));
		setSaved(false);
		setTouchedSecrets((prev) => new Set(prev).add(key));
	}

	async function handleSave() {
		setSaving(true);
		setError(null);
		try {
			const payload: Record<string, string> = {};
			for (const property of service.properties) {
				if (touchedSecrets.has(property.key)) {
					payload[property.key] = values[property.key] ?? "";
				}
			}
			const updated = await updateExternalServiceProperties(service.serviceKey, payload);
			setTouchedSecrets(new Set());
			setValues(Object.fromEntries(updated.properties.map((property) => [property.key, ""])));
			setSaved(true);
			onSaved(updated);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to save credentials");
		} finally {
			setSaving(false);
		}
	}

	if (!service.enabled) {
		return (
			<Paper sx={{ p: 3 }} variant="outlined">
				<Stack spacing={0.5}>
					<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
						<Typography variant="h6">{service.displayName}</Typography>
						<Chip label="Not enabled" size="small" />
					</Box>
					<Typography variant="body2" color="text.secondary">
						Ask your platform administrator to enable this integration for your organisation.
					</Typography>
				</Stack>
			</Paper>
		);
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">{service.displayName}</Typography>
					<Chip label="Enabled" size="small" color="success" />
				</Box>

				{error && <Alert severity="error">{error}</Alert>}
				{saved && !error && <Alert severity="success">Saved.</Alert>}

				<Stack spacing={2}>
					{service.properties.map((property) => (
						<TextField
							key={property.key}
							label={formatPropertyLabel(property.key)}
							type={property.secret ? "password" : "text"}
							value={values[property.key] ?? ""}
							onChange={(e) => handleChange(property.key, e.target.value)}
							placeholder={property.configured && !touchedSecrets.has(property.key) ? "•••••••• (configured)" : undefined}
							disabled={!canEdit}
							fullWidth
							size="small"
						/>
					))}
				</Stack>

				{canEdit && (
					<Box>
						<Button variant="contained" onClick={handleSave} disabled={saving}>
							Save
						</Button>
					</Box>
				)}
			</Stack>
		</Paper>
	);
}

/** Institution's own view of/control over its integrations - the BYOK counterpart to the
 * platform admin's entitlement toggle on OrganisationDetailPage. A service the platform
 * hasn't enabled shows locked rather than being hidden, so the institution knows it
 * exists. Credential values never round-trip back from the API once saved - only whether
 * one is configured (see ExternalServicePropertyStatusResponse). */
export function ExternalServicesPanel() {
	const { hasAnyPermission } = useAuth();
	const canEdit = hasAnyPermission(["ORGANISATION_MANAGE"]);

	const [services, setServices] = useState<ExternalServiceSettingsResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	function refresh() {
		listExternalServices()
			.then(setServices)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load integrations"));
	}

	useEffect(refresh, []);

	function handleSaved(updated: ExternalServiceSettingsResponse) {
		setServices((prev) => prev.map((service) => (service.serviceKey === updated.serviceKey ? updated : service)));
	}

	return (
		<Stack spacing={2}>
			{error && <Alert severity="error">{error}</Alert>}

			{services.length === 0 && !error && <Alert severity="info">No integrations available yet.</Alert>}

			{services.map((service) => (
				<ServiceCard key={service.serviceKey} service={service} canEdit={canEdit} onSaved={handleSaved} />
			))}
		</Stack>
	);
}
