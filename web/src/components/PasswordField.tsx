import { useState } from "react";
import IconButton from "@mui/material/IconButton";
import InputAdornment from "@mui/material/InputAdornment";
import TextField, { type TextFieldProps } from "@mui/material/TextField";
import VisibilityIcon from "@mui/icons-material/Visibility";
import VisibilityOffIcon from "@mui/icons-material/VisibilityOff";

/** A TextField for passwords with a show/hide eye toggle. */
export function PasswordField(props: Omit<TextFieldProps, "type">) {
	const [visible, setVisible] = useState(false);
	return (
		<TextField
			{...props}
			type={visible ? "text" : "password"}
			sx={{
				// Edge/IE draw their own reveal button; hide it so only ours shows.
				"& input::-ms-reveal, & input::-ms-clear": { display: "none" },
				...props.sx,
			}}
			slotProps={{
				...props.slotProps,
				input: {
					...(props.slotProps?.input as object),
					endAdornment: (
						<InputAdornment position="end">
							<IconButton
								aria-label={visible ? "Hide password" : "Show password"}
								onClick={() => setVisible((v) => !v)}
								onMouseDown={(e) => e.preventDefault()}
								edge="end"
							>
								{visible ? <VisibilityOffIcon /> : <VisibilityIcon />}
							</IconButton>
						</InputAdornment>
					),
				},
			}}
		/>
	);
}
