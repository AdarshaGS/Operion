import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import Autocomplete from "@mui/material/Autocomplete";
import Avatar from "@mui/material/Avatar";
import Box from "@mui/material/Box";
import ButtonBase from "@mui/material/ButtonBase";
import InputBase from "@mui/material/InputBase";
import Tooltip from "@mui/material/Tooltip";
import Typography from "@mui/material/Typography";
import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import SearchIcon from "@mui/icons-material/Search";
import { colors } from "../theme";

/** A bordered, rounded control used for the campus / year / date chips in the top bar. */
export function TopbarPill({
	icon,
	label,
	ariaLabel,
	onClick,
	hideLabelOnPhone = true,
	chevron = true,
}: {
	icon: ReactNode;
	label: string;
	ariaLabel?: string;
	onClick?: (event: React.MouseEvent<HTMLElement>) => void;
	hideLabelOnPhone?: boolean;
	chevron?: boolean;
}) {
	const interactive = onClick !== undefined;
	return (
		<ButtonBase
			component={interactive ? "button" : "div"}
			onClick={onClick}
			aria-label={ariaLabel ?? label}
			disabled={!interactive}
			sx={{
				display: "flex",
				alignItems: "center",
				gap: 1,
				height: 44,
				px: { xs: 1.25, sm: 1.75 },
				borderRadius: "12px",
				backgroundColor: colors.paper,
				border: `1px solid ${colors.rule}`,
				color: colors.ink,
				flexShrink: 0,
				"&.Mui-disabled": { opacity: 1, pointerEvents: "none" },
				"&:hover": { borderColor: colors.accent },
				"& .MuiSvgIcon-root": { color: colors.accent, fontSize: 20 },
			}}
		>
			{icon}
			<Typography variant="body2" sx={{ fontWeight: 600, display: hideLabelOnPhone ? { xs: "none", sm: "block" } : "block", whiteSpace: "nowrap" }}>
				{label}
			</Typography>
			{interactive && chevron && <KeyboardArrowDownIcon sx={{ display: { xs: "none", sm: "block" }, color: `${colors.inkFaint} !important` }} />}
		</ButtonBase>
	);
}

export interface SearchOption {
	label: string;
	sublabel?: string;
	path: string;
	group: string;
}

interface GlobalSearchProps {
	placeholder?: string;
	/** Screens the caller can open - matched locally by label. */
	pages: { label: string; path: string }[];
	/** Optional remote lookups (students, organisations...) merged in under their own group. */
	lookup?: (query: string) => Promise<SearchOption[]>;
}

/** Top-bar search: jumps to a screen by name, plus whatever record lookup the shell supplies.
 * Cmd/Ctrl+K focuses it from anywhere. */
export function GlobalSearch({ placeholder = "Search students, staff, classes, anything...", pages, lookup }: GlobalSearchProps) {
	const navigate = useNavigate();
	const inputRef = useRef<HTMLInputElement | null>(null);
	const [input, setInput] = useState("");
	const [remote, setRemote] = useState<SearchOption[]>([]);
	const isMac = typeof navigator !== "undefined" && /mac/i.test(navigator.platform);

	useEffect(() => {
		function onKey(event: KeyboardEvent) {
			if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
				event.preventDefault();
				inputRef.current?.focus();
			}
		}
		window.addEventListener("keydown", onKey);
		return () => window.removeEventListener("keydown", onKey);
	}, []);

	useEffect(() => {
		const query = input.trim();
		if (!lookup || query.length < 2) {
			setRemote([]);
			return undefined;
		}
		let cancelled = false;
		const timer = setTimeout(() => {
			lookup(query)
				.then((results) => {
					if (!cancelled) setRemote(results);
				})
				.catch(() => {
					if (!cancelled) setRemote([]);
				});
		}, 250);
		return () => {
			cancelled = true;
			clearTimeout(timer);
		};
	}, [input, lookup]);

	const options = useMemo<SearchOption[]>(() => {
		const query = input.trim().toLowerCase();
		if (!query) return [];
		const matchedPages = pages
			.filter((page) => page.label.toLowerCase().includes(query))
			.slice(0, 5)
			.map((page) => ({ label: page.label, path: page.path, group: "Go to" }));
		return [...matchedPages, ...remote];
	}, [input, pages, remote]);

	function go(option: SearchOption | string | null) {
		const target = typeof option === "string" ? options[0] : option;
		if (!target) return;
		navigate(target.path);
		setInput("");
		setRemote([]);
		inputRef.current?.blur();
	}

	return (
		<Autocomplete<SearchOption, false, false, true>
			freeSolo
			fullWidth
			blurOnSelect
			clearOnBlur={false}
			options={options}
			filterOptions={(all) => all}
			groupBy={(option) => option.group}
			getOptionLabel={(option) => (typeof option === "string" ? option : option.label)}
			inputValue={input}
			onInputChange={(_, value, reason) => setInput(reason === "reset" ? "" : value)}
			onChange={(_, value) => go(value)}
			slotProps={{ paper: { sx: { mt: 0.75 } } }}
			sx={{ maxWidth: 560, flexGrow: 1, minWidth: 0 }}
			renderOption={(props, option) => {
				const { key, ...rest } = props as typeof props & { key: string };
				return (
					<Box component="li" key={key} {...rest}>
						<Box sx={{ minWidth: 0 }}>
							<Typography variant="body2" sx={{ fontWeight: 600 }} noWrap>
								{option.label}
							</Typography>
							{option.sublabel && (
								<Typography variant="caption" color="text.secondary" noWrap sx={{ display: "block" }}>
									{option.sublabel}
								</Typography>
							)}
						</Box>
					</Box>
				);
			}}
			renderInput={(params) => (
				<Box
					ref={params.slotProps.input.ref}
					sx={{
						display: "flex",
						alignItems: "center",
						gap: 1,
						height: 44,
						px: 1.75,
						borderRadius: "12px",
						backgroundColor: colors.paper,
						border: `1px solid ${colors.rule}`,
						"&:focus-within": { borderColor: colors.accent },
					}}
				>
					<SearchIcon sx={{ color: colors.inkFaint, fontSize: 20 }} />
					<InputBase
						id={params.id}
						fullWidth
						inputProps={{
							...params.slotProps.htmlInput,
							"aria-label": "Search",
							ref: (node: HTMLInputElement | null) => {
								inputRef.current = node;
								const forwarded = (params.slotProps.htmlInput as { ref?: React.Ref<HTMLInputElement> }).ref;
								if (typeof forwarded === "function") forwarded(node);
								else if (forwarded) (forwarded as React.RefObject<HTMLInputElement | null>).current = node;
							},
						}}
						placeholder={placeholder}
						sx={{ flexGrow: 1, fontSize: "0.9rem", minWidth: 0 }}
					/>
					<Typography
						variant="caption"
						sx={{
							display: { xs: "none", lg: "block" },
							px: 0.75,
							py: 0.25,
							borderRadius: 1,
							border: `1px solid ${colors.rule}`,
							color: colors.inkFaint,
							fontWeight: 600,
							whiteSpace: "nowrap",
						}}
					>
						{isMac ? "⌘" : "Ctrl"} K
					</Typography>
				</Box>
			)}
		/>
	);
}

function initials(name: string | null): string {
	if (!name) return "?";
	return name
		.split(" ")
		.filter(Boolean)
		.map((part) => part[0])
		.join("")
		.slice(0, 2)
		.toUpperCase();
}

/** Compact account trigger: just the avatar and a small chevron - name, role and e-mail live in the menu it opens. */
export function AccountButton({ name, subtitle, onClick }: { name: string | null; subtitle: string; onClick: (event: React.MouseEvent<HTMLElement>) => void }) {
	return (
		<Tooltip title={name ? `${name} · ${subtitle}` : subtitle}>
			<ButtonBase
				onClick={onClick}
				aria-label="Account menu"
				aria-haspopup="true"
				sx={{ display: "flex", alignItems: "center", gap: 0.25, p: 0.25, pr: 0.5, borderRadius: "12px", flexShrink: 0, "&:hover": { backgroundColor: colors.paper } }}
			>
				<Avatar sx={{ width: 36, height: 36, fontSize: 13, fontWeight: 700, color: colors.accentInk, background: `linear-gradient(135deg, ${colors.accent} 0%, ${colors.accentStrong} 100%)` }}>
					{initials(name)}
				</Avatar>
				<KeyboardArrowDownIcon sx={{ fontSize: 18, color: colors.inkFaint }} />
			</ButtonBase>
		</Tooltip>
	);
}
