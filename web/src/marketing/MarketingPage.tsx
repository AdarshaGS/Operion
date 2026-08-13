import { useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Link from "@mui/material/Link";
import Slider from "@mui/material/Slider";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { Seal, Wordmark } from "../branding/Wordmark";
import { colors, fontDisplay } from "../theme";

const PRICE_PER_STUDENT = 150;

const PAIN_ROWS = [
	{
		before: "Attendance marked on paper, re-typed into Excel for the office.",
		after: "Marked once on a tablet or phone, locked at day's end — with a correction trail if it's wrong.",
	},
	{
		before: "Fee reminders and admission notices sent one by one on WhatsApp.",
		after: "One announcement, fanned out to the right parents automatically — with read receipts.",
	},
	{
		before: "Report cards assembled by hand from five different mark sheets.",
		after: "Marks entered once per subject; report cards compute and publish themselves.",
	},
];

const TRUST_ITEMS = [
	{ stat: "11 modules", detail: "one login, one tenant" },
	{ stat: "Multi-campus", detail: "multi-year by design" },
	{ stat: "Every record", detail: "has a paper trail" },
	{ stat: "₹100–₹200", detail: "per student, per year" },
];

function FakeRow({ width, pillColor }: { width: string; pillColor?: string }) {
	return (
		<Box sx={{ display: "flex", alignItems: "center", gap: 0.7 }}>
			<Box sx={{ height: 8, borderRadius: 0.5, bgcolor: colors.rule, width }} />
			{pillColor && <Box sx={{ height: 14, width: 46, borderRadius: 999, bgcolor: pillColor, flexShrink: 0 }} />}
		</Box>
	);
}

export function MarketingPage() {
	const [students, setStudents] = useState(800);
	const amount = students * PRICE_PER_STUDENT;

	return (
		<Box sx={{ bgcolor: colors.paper, color: colors.ink, minHeight: "100vh" }}>
			{/* top nav */}
			<Box sx={{ bgcolor: colors.paperRaised, borderBottom: `1px solid ${colors.rule}` }}>
				<Box sx={{ maxWidth: 1180, mx: "auto", px: { xs: 2.5, md: 4 }, py: 1.5, display: "flex", alignItems: "center", gap: 3 }}>
					<Wordmark size="small" />
					<Stack direction="row" spacing={3} sx={{ ml: 2, flexGrow: 1, display: { xs: "none", sm: "flex" } }}>
						{["Product", "Pricing", "Schools"].map((label) => (
							<Link key={label} href={`#${label.toLowerCase()}`} underline="none" color={colors.inkSoft} sx={{ fontSize: "0.88rem" }}>
								{label}
							</Link>
						))}
						<Link component={RouterLink} to="/login" underline="none" color={colors.inkSoft} sx={{ fontSize: "0.88rem" }}>
							Log in
						</Link>
					</Stack>
					<Box sx={{ flexGrow: { xs: 1, sm: 0 } }} />
					<Button component={RouterLink} to="/login" variant="contained">
						Book a walkthrough
					</Button>
				</Box>
			</Box>

			<Box sx={{ maxWidth: 1180, mx: "auto", px: { xs: 2.5, md: 4 } }}>
				{/* hero */}
				<Box
					sx={{
						display: "grid",
						gridTemplateColumns: { xs: "1fr", md: "1.05fr 0.95fr" },
						gap: { xs: 4, md: 6 },
						alignItems: "center",
						py: { xs: 5, md: 7 },
					}}
				>
					<Box>
						<Typography variant="overline" color="text.secondary">
							School administration software — India
						</Typography>
						<Typography
							variant="h2"
							component="h1"
							sx={{ fontSize: { xs: "2.1rem", md: "3rem" }, lineHeight: 1.08, my: 1.5, textWrap: "balance" }}
						>
							Every register your school keeps. In one place that never loses a page.
						</Typography>
						<Typography variant="body1" sx={{ color: colors.inkSoft, fontSize: "1.05rem", maxWidth: "46ch", mb: 3 }}>
							Operion replaces the attendance register, the fee ledger, the WhatsApp groups and the report-card spreadsheets your
							office already runs on — built for how Indian schools actually work, priced per student, not per seat.
						</Typography>
						<Stack direction="row" spacing={1.5} sx={{ flexWrap: "wrap", gap: 1.5 }}>
							<Button component={RouterLink} to="/login" variant="contained" size="large">
								Book a walkthrough
							</Button>
							<Button href="#pricing" variant="outlined" size="large">
								See pricing →
							</Button>
						</Stack>
					</Box>

					<Box sx={{ position: "relative", height: { xs: 240, md: 320 }, display: { xs: "none", sm: "block" } }} aria-hidden="true">
						<Box
							sx={{
								position: "absolute",
								width: 128,
								height: 190,
								borderRadius: "6px 10px 10px 6px",
								bgcolor: colors.ruleStrong,
								top: 18,
								left: 4,
								transform: "rotate(-7deg)",
								boxShadow: "0 1px 2px rgba(22,35,58,0.09)",
								display: "flex",
								alignItems: "flex-end",
								p: 1,
							}}
						>
							<Typography sx={{ fontFamily: fontDisplay, fontSize: "0.62rem", letterSpacing: "0.1em", color: colors.accentInk, writingMode: "vertical-rl", textTransform: "uppercase" }}>
								Attendance
							</Typography>
						</Box>
						<Box
							sx={{
								position: "absolute",
								width: 128,
								height: 190,
								borderRadius: "6px 10px 10px 6px",
								bgcolor: colors.warn,
								top: 34,
								right: 6,
								transform: "rotate(6deg)",
								boxShadow: "0 1px 2px rgba(22,35,58,0.09)",
								display: "flex",
								alignItems: "flex-end",
								p: 1,
							}}
						>
							<Typography sx={{ fontFamily: fontDisplay, fontSize: "0.62rem", letterSpacing: "0.1em", color: colors.accentInk, writingMode: "vertical-rl", textTransform: "uppercase" }}>
								Fees
							</Typography>
						</Box>
						<Box
							sx={{
								position: "absolute",
								top: 46,
								left: 60,
								right: 60,
								bottom: 10,
								bgcolor: colors.paperRaised,
								border: `1px solid ${colors.rule}`,
								borderRadius: 2,
								boxShadow: "0 28px 60px -26px rgba(22,35,58,0.5)",
								overflow: "hidden",
							}}
						>
							<Box sx={{ display: "flex", gap: 0.5, px: 1.2, py: 1, bgcolor: colors.paperSunken, borderBottom: `1px solid ${colors.rule}` }}>
								{[colors.bad, colors.warn, colors.ok].map((c) => (
									<Box key={c} sx={{ width: 7, height: 7, borderRadius: "50%", bgcolor: c }} />
								))}
							</Box>
							<Stack spacing={1} sx={{ p: 1.6 }}>
								<FakeRow width="60%" />
								<FakeRow width="52%" pillColor={colors.okBg} />
								<FakeRow width="46%" pillColor={colors.warnBg} />
								<FakeRow width="58%" pillColor={colors.okBg} />
								<FakeRow width="40%" pillColor={colors.badBg} />
							</Stack>
						</Box>
					</Box>
				</Box>

				{/* trust strip */}
				<Box
					sx={{
						borderTop: `1px solid ${colors.rule}`,
						borderBottom: `1px solid ${colors.rule}`,
						py: 2.5,
						display: "grid",
						gridTemplateColumns: { xs: "1fr 1fr", md: "repeat(4, 1fr)" },
						gap: 2.5,
					}}
				>
					{TRUST_ITEMS.map((item) => (
						<Box key={item.stat} sx={{ pl: 1.5, borderLeft: `2px solid ${colors.ruleStrong}` }}>
							<Typography sx={{ fontFamily: fontDisplay, fontWeight: 700, fontSize: "0.95rem" }}>{item.stat}</Typography>
							<Typography variant="body2" color="text.secondary">
								{item.detail}
							</Typography>
						</Box>
					))}
				</Box>

				{/* pain / solution */}
				<Box id="product" sx={{ py: 6 }}>
					<Typography variant="h5" sx={{ mb: 0.5 }}>
						What it actually replaces
					</Typography>
					<Typography sx={{ color: colors.inkSoft, maxWidth: "55ch", mb: 3 }}>
						Not abstractions — the specific things your office does every day, and what happens to each one.
					</Typography>
					<Stack>
						{PAIN_ROWS.map((row, i) => (
							<Box
								key={row.before}
								sx={{
									display: "grid",
									gridTemplateColumns: { xs: "1fr", md: "1fr auto 1fr" },
									alignItems: "center",
									gap: 2,
									py: 2,
									borderTop: `1px solid ${colors.rule}`,
									borderBottom: i === PAIN_ROWS.length - 1 ? `1px solid ${colors.rule}` : "none",
								}}
							>
								<Box>
									<Typography sx={{ fontFamily: fontDisplay, fontSize: "0.62rem", letterSpacing: "0.1em", textTransform: "uppercase", color: colors.inkFaint, mb: 0.4 }}>
										Today
									</Typography>
									<Typography sx={{ color: colors.inkFaint, fontSize: "0.92rem" }}>{row.before}</Typography>
								</Box>
								<Typography sx={{ fontFamily: fontDisplay, color: colors.ruleStrong, display: { xs: "none", md: "block" } }}>→</Typography>
								<Box>
									<Typography sx={{ fontFamily: fontDisplay, fontSize: "0.62rem", letterSpacing: "0.1em", textTransform: "uppercase", color: colors.accent, mb: 0.4 }}>
										With Operion
									</Typography>
									<Typography sx={{ fontSize: "0.94rem" }}>{row.after}</Typography>
								</Box>
							</Box>
						))}
					</Stack>
				</Box>

				{/* pricing */}
				<Box id="pricing" sx={{ pb: 7 }}>
					<Box
						sx={{
							bgcolor: colors.paperRaised,
							border: `1px solid ${colors.rule}`,
							borderRadius: 2,
							p: { xs: 3, md: 4 },
							display: "grid",
							gridTemplateColumns: { xs: "1fr", md: "1fr 1fr" },
							gap: 4,
							alignItems: "center",
							boxShadow: "0 1px 2px rgba(22,35,58,0.09)",
						}}
					>
						<Box>
							<Typography variant="h6" sx={{ mb: 0.5 }}>
								Priced like a school budget, not a SaaS trial
							</Typography>
							<Typography sx={{ color: colors.inkSoft, fontSize: "0.92rem", maxWidth: "34ch" }}>
								Per student, per year. No per-admin seat fees, no module upsells — every module is included.
							</Typography>
						</Box>
						<Box>
							<Typography
								sx={{ fontFamily: fontDisplay, fontSize: "0.78rem", textTransform: "uppercase", letterSpacing: "0.06em", color: colors.inkSoft, mb: 1 }}
							>
								Your school's student count
							</Typography>
							<Slider
								value={students}
								min={200}
								max={3000}
								step={50}
								onChange={(_, value) => setStudents(value as number)}
								sx={{ color: colors.accent }}
							/>
							<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", mt: 0.5 }}>
								<Typography sx={{ fontFamily: fontDisplay, fontSize: "1.6rem", fontWeight: 700 }}>
									₹{amount.toLocaleString("en-IN")}
								</Typography>
								<Typography variant="body2" color="text.secondary">
									est. per year for {students.toLocaleString("en-IN")} students, at ₹{PRICE_PER_STUDENT}/student
								</Typography>
							</Box>
						</Box>
					</Box>
				</Box>
			</Box>

			<Box sx={{ borderTop: `1px solid ${colors.rule}`, py: 3 }}>
				<Box
					sx={{
						maxWidth: 1180,
						mx: "auto",
						px: { xs: 2.5, md: 4 },
						display: "flex",
						justifyContent: "space-between",
						alignItems: "center",
						flexWrap: "wrap",
						gap: 1,
					}}
				>
					<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
						<Seal size={20} />
						<Typography variant="body2" color="text.secondary">
							Operion — school administration software
						</Typography>
					</Stack>
					<Link component={RouterLink} to="/login" underline="hover" variant="body2" color={colors.inkSoft}>
						Sign in to your school's portal →
					</Link>
				</Box>
			</Box>
		</Box>
	);
}
