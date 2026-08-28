import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import { getOrganisationProfile, updateOrganisationProfile } from "../../api/organisationProfile";

/** Organisation Profile (GitHub #141) - name/legal name, logo, primary contact, address,
 * and tax/legal identifier. Distinct from Business settings (timezone/currency/working
 * days) even though logo lives on the same underlying record - one field, two forms that
 * can both reach it, no duplicated storage. */
export function OrganisationProfilePanel() {
	const [name, setName] = useState("");
	const [legalName, setLegalName] = useState("");
	const [logoUrl, setLogoUrl] = useState("");
	const [primaryContactName, setPrimaryContactName] = useState("");
	const [primaryContactEmail, setPrimaryContactEmail] = useState("");
	const [primaryContactPhone, setPrimaryContactPhone] = useState("");
	const [addressLine1, setAddressLine1] = useState("");
	const [addressLine2, setAddressLine2] = useState("");
	const [city, setCity] = useState("");
	const [state, setState] = useState("");
	const [country, setCountry] = useState("");
	const [pincode, setPincode] = useState("");
	const [taxIdentifier, setTaxIdentifier] = useState("");
	const [error, setError] = useState<string | null>(null);
	const [saved, setSaved] = useState(false);
	const [loading, setLoading] = useState(true);
	const [submitting, setSubmitting] = useState(false);

	useEffect(() => {
		getOrganisationProfile()
			.then((profile) => {
				setName(profile.name ?? "");
				setLegalName(profile.legalName ?? "");
				setLogoUrl(profile.logoUrl ?? "");
				setPrimaryContactName(profile.primaryContactName ?? "");
				setPrimaryContactEmail(profile.primaryContactEmail ?? "");
				setPrimaryContactPhone(profile.primaryContactPhone ?? "");
				setAddressLine1(profile.addressLine1 ?? "");
				setAddressLine2(profile.addressLine2 ?? "");
				setCity(profile.city ?? "");
				setState(profile.state ?? "");
				setCountry(profile.country ?? "");
				setPincode(profile.pincode ?? "");
				setTaxIdentifier(profile.taxIdentifier ?? "");
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load organisation profile"))
			.finally(() => setLoading(false));
	}, []);

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		setSaved(false);
		try {
			await updateOrganisationProfile({
				name,
				legalName: legalName || null,
				logoUrl: logoUrl || null,
				primaryContactName: primaryContactName || null,
				primaryContactEmail: primaryContactEmail || null,
				primaryContactPhone: primaryContactPhone || null,
				addressLine1: addressLine1 || null,
				addressLine2: addressLine2 || null,
				city: city || null,
				state: state || null,
				country: country || null,
				pincode: pincode || null,
				taxIdentifier: taxIdentifier || null,
			});
			setSaved(true);
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update organisation profile");
		} finally {
			setSubmitting(false);
		}
	}

	if (loading) {
		return null;
	}

	return (
		<Paper component="form" onSubmit={handleSubmit} sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="h6">Organisation profile</Typography>

				{error && <Alert severity="error">{error}</Alert>}
				{saved && <Alert severity="success">Profile updated</Alert>}

				<Box sx={{ display: "flex", gap: 2 }}>
					<TextField label="Organisation name" value={name} onChange={(e) => setName(e.target.value)} required fullWidth />
					<TextField label="Legal name" value={legalName} onChange={(e) => setLegalName(e.target.value)} fullWidth />
				</Box>
				<TextField label="Logo URL" value={logoUrl} onChange={(e) => setLogoUrl(e.target.value)} fullWidth />

				<Typography variant="subtitle2" sx={{ mt: 1 }}>
					Primary contact
				</Typography>
				<Box sx={{ display: "flex", gap: 2 }}>
					<TextField label="Contact name" value={primaryContactName} onChange={(e) => setPrimaryContactName(e.target.value)} fullWidth />
					<TextField
						label="Contact email"
						type="email"
						value={primaryContactEmail}
						onChange={(e) => setPrimaryContactEmail(e.target.value)}
						fullWidth
					/>
					<TextField label="Contact phone" value={primaryContactPhone} onChange={(e) => setPrimaryContactPhone(e.target.value)} fullWidth />
				</Box>

				<Typography variant="subtitle2" sx={{ mt: 1 }}>
					Address
				</Typography>
				<TextField label="Address line 1" value={addressLine1} onChange={(e) => setAddressLine1(e.target.value)} fullWidth />
				<TextField label="Address line 2" value={addressLine2} onChange={(e) => setAddressLine2(e.target.value)} fullWidth />
				<Box sx={{ display: "flex", gap: 2 }}>
					<TextField label="City" value={city} onChange={(e) => setCity(e.target.value)} fullWidth />
					<TextField label="State" value={state} onChange={(e) => setState(e.target.value)} fullWidth />
					<TextField label="Country" value={country} onChange={(e) => setCountry(e.target.value)} fullWidth />
					<TextField label="Pincode" value={pincode} onChange={(e) => setPincode(e.target.value)} fullWidth />
				</Box>

				<TextField
					label="Legal / tax identifier"
					placeholder="GSTIN, PAN, or equivalent"
					value={taxIdentifier}
					onChange={(e) => setTaxIdentifier(e.target.value)}
					fullWidth
				/>

				<Box sx={{ display: "flex", justifyContent: "flex-end" }}>
					<Button type="submit" variant="contained" disabled={submitting}>
						{submitting ? "Saving..." : "Save"}
					</Button>
				</Box>
			</Stack>
		</Paper>
	);
}
