import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Badge from "@mui/material/Badge";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Divider from "@mui/material/Divider";
import List from "@mui/material/List";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import {
	listMessages,
	listThreads,
	markThreadRead,
	openClassGroupThread,
	openDirectThread,
	sendMessage,
	type MessageResponse,
	type MessageThreadResponse,
} from "../../api/messaging";
import { subscribeToThread } from "../../api/messagingSocket";
import { type PersonResponse, listPersons } from "../../api/persons";
import { listSchoolClasses, type SchoolClassResponse } from "../../api/schoolClasses";
import { listSections, type SectionResponse } from "../../api/sections";

function threadTitle(thread: MessageThreadResponse): string {
	if (thread.type === "CLASS_GROUP") return thread.sectionLabel ?? `Class group #${thread.id}`;
	return thread.participants.map((p) => p.name).join(", ") || "Direct message";
}

export function MessagingPage() {
	const [threads, setThreads] = useState<MessageThreadResponse[]>([]);
	const [selectedThreadId, setSelectedThreadId] = useState<number | null>(null);
	const [messages, setMessages] = useState<MessageResponse[]>([]);
	const [draft, setDraft] = useState("");
	const [error, setError] = useState<string | null>(null);
	const [dmDialogOpen, setDmDialogOpen] = useState(false);
	const [groupDialogOpen, setGroupDialogOpen] = useState(false);
	const messageListRef = useRef<HTMLDivElement>(null);

	function refreshThreads() {
		listThreads().then(setThreads).catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load threads"));
	}

	useEffect(refreshThreads, []);

	useEffect(() => {
		if (selectedThreadId == null) return;
		listMessages(selectedThreadId)
			.then(setMessages)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load messages"));
		markThreadRead(selectedThreadId)
			.then(refreshThreads)
			.catch(() => {});

		return subscribeToThread(selectedThreadId, (incoming) => {
			setMessages((current) => (current.some((m) => m.id === incoming.id) ? current : [...current, incoming]));
			refreshThreads();
		});
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [selectedThreadId]);

	useEffect(() => {
		messageListRef.current?.scrollTo({ top: messageListRef.current.scrollHeight });
	}, [messages]);

	const selectedThread = useMemo(() => threads.find((t) => t.id === selectedThreadId) ?? null, [threads, selectedThreadId]);

	async function handleSend(event: FormEvent) {
		event.preventDefault();
		if (!selectedThreadId || !draft.trim()) return;
		try {
			const sent = await sendMessage(selectedThreadId, draft.trim());
			setMessages((current) => (current.some((m) => m.id === sent.id) ? current : [...current, sent]));
			setDraft("");
			refreshThreads();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to send message");
		}
	}

	return (
		<Stack direction="row" spacing={2} sx={{ height: "calc(100vh - 160px)" }}>
			<Paper sx={{ width: 320, display: "flex", flexDirection: "column" }}>
				<Stack direction="row" spacing={1} sx={{ p: 2 }}>
					<Button size="small" variant="outlined" onClick={() => setDmDialogOpen(true)}>
						New message
					</Button>
					<Button size="small" variant="outlined" onClick={() => setGroupDialogOpen(true)}>
						Class group
					</Button>
				</Stack>
				<Divider />
				{error && (
					<Alert severity="error" onClose={() => setError(null)}>
						{error}
					</Alert>
				)}
				<List sx={{ overflowY: "auto", flex: 1 }}>
					{threads.length === 0 && (
						<Typography variant="body2" color="text.secondary" sx={{ p: 2 }}>
							No conversations yet.
						</Typography>
					)}
					{threads.map((thread) => (
						<ListItemButton key={thread.id} selected={thread.id === selectedThreadId} onClick={() => setSelectedThreadId(thread.id)}>
							<ListItemText
								primary={threadTitle(thread)}
								secondary={thread.lastMessageAt ? new Date(thread.lastMessageAt).toLocaleString() : "No messages yet"}
							/>
							{thread.unread && <Badge color="primary" variant="dot" sx={{ ml: 1 }} />}
						</ListItemButton>
					))}
				</List>
			</Paper>

			<Paper sx={{ flex: 1, display: "flex", flexDirection: "column" }}>
				{!selectedThread && (
					<Box sx={{ p: 3 }}>
						<Typography color="text.secondary">Select a conversation, or start a new one.</Typography>
					</Box>
				)}
				{selectedThread && (
					<>
						<Box sx={{ p: 2 }}>
							<Typography variant="h6">{threadTitle(selectedThread)}</Typography>
						</Box>
						<Divider />
						<Box ref={messageListRef} sx={{ flex: 1, overflowY: "auto", p: 2 }}>
							<Stack spacing={1.5}>
								{messages.map((message) => (
									<Box key={message.id}>
										<Typography variant="caption" color="text.secondary">
											{message.senderName} &middot; {new Date(message.sentAt).toLocaleString()}
										</Typography>
										<Typography variant="body1">{message.body}</Typography>
									</Box>
								))}
							</Stack>
						</Box>
						<Divider />
						<Stack direction="row" spacing={1} component="form" onSubmit={handleSend} sx={{ p: 2 }}>
							<TextField
								fullWidth
								size="small"
								placeholder="Type a message"
								value={draft}
								onChange={(e) => setDraft(e.target.value)}
							/>
							<Button type="submit" variant="contained" disabled={!draft.trim()}>
								Send
							</Button>
						</Stack>
					</>
				)}
			</Paper>

			<NewDirectMessageDialog
				open={dmDialogOpen}
				onClose={() => setDmDialogOpen(false)}
				onOpened={(thread) => {
					refreshThreads();
					setSelectedThreadId(thread.id);
					setDmDialogOpen(false);
				}}
				onError={setError}
			/>
			<OpenClassGroupDialog
				open={groupDialogOpen}
				onClose={() => setGroupDialogOpen(false)}
				onOpened={(thread) => {
					refreshThreads();
					setSelectedThreadId(thread.id);
					setGroupDialogOpen(false);
				}}
				onError={setError}
			/>
		</Stack>
	);
}

function NewDirectMessageDialog({
	open,
	onClose,
	onOpened,
	onError,
}: {
	open: boolean;
	onClose: () => void;
	onOpened: (thread: MessageThreadResponse) => void;
	onError: (message: string) => void;
}) {
	const [persons, setPersons] = useState<PersonResponse[]>([]);
	const [personId, setPersonId] = useState("");

	useEffect(() => {
		if (open) listPersons().then(setPersons).catch(() => {});
	}, [open]);

	async function handleOpen() {
		if (!personId) return;
		try {
			onOpened(await openDirectThread(Number(personId)));
			setPersonId("");
		} catch (err) {
			onError(err instanceof ApiError ? err.message : "Failed to start conversation");
		}
	}

	return (
		<Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
			<DialogTitle>Start a direct message</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					<TextField select label="Person" value={personId} onChange={(e) => setPersonId(e.target.value)}>
						{persons.map((person) => (
							<MenuItem key={person.id} value={person.id}>
								{person.firstName} {person.lastName}
							</MenuItem>
						))}
					</TextField>
					<Button variant="contained" disabled={!personId} onClick={handleOpen}>
						Start
					</Button>
				</Stack>
			</DialogContent>
		</Dialog>
	);
}

function OpenClassGroupDialog({
	open,
	onClose,
	onOpened,
	onError,
}: {
	open: boolean;
	onClose: () => void;
	onOpened: (thread: MessageThreadResponse) => void;
	onError: (message: string) => void;
}) {
	const [classes, setClasses] = useState<SchoolClassResponse[]>([]);
	const [sections, setSections] = useState<SectionResponse[]>([]);
	const [classId, setClassId] = useState("");
	const [sectionId, setSectionId] = useState("");

	useEffect(() => {
		if (open) listSchoolClasses().then(setClasses).catch(() => {});
	}, [open]);

	useEffect(() => {
		setSectionId("");
		if (!classId) {
			setSections([]);
			return;
		}
		listSections(Number(classId)).then(setSections).catch(() => {});
	}, [classId]);

	async function handleOpen() {
		if (!sectionId) return;
		try {
			onOpened(await openClassGroupThread(Number(sectionId)));
			setClassId("");
			setSectionId("");
		} catch (err) {
			onError(err instanceof ApiError ? err.message : "Failed to open class group");
		}
	}

	return (
		<Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
			<DialogTitle>Open a class group</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ mt: 1 }}>
					<TextField select label="Class" value={classId} onChange={(e) => setClassId(e.target.value)}>
						{classes.map((schoolClass) => (
							<MenuItem key={schoolClass.id} value={schoolClass.id}>
								{schoolClass.displayName ?? `Class #${schoolClass.id}`}
							</MenuItem>
						))}
					</TextField>
					<TextField select label="Section" value={sectionId} onChange={(e) => setSectionId(e.target.value)} disabled={!classId}>
						{sections.map((section) => (
							<MenuItem key={section.id} value={section.id}>
								{section.name}
							</MenuItem>
						))}
					</TextField>
					<Button variant="contained" disabled={!sectionId} onClick={handleOpen}>
						Open
					</Button>
				</Stack>
			</DialogContent>
		</Dialog>
	);
}
