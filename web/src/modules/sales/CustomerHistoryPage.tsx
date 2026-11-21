import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
import IconButton from "@mui/material/IconButton";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Tooltip from "@mui/material/Tooltip";
import Typography from "@mui/material/Typography";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { ApiError } from "../../api/client";
import { listCustomers, type CustomerResponse } from "../../api/customers";
import { listSales, type SaleResponse } from "../../api/sales";
import { SALE_STATUS_COLOR } from "./SalesPanel";

export function CustomerHistoryPage() {
	const { customerId } = useParams<{ customerId: string }>();
	const navigate = useNavigate();

	const [customer, setCustomer] = useState<CustomerResponse | null>(null);
	const [sales, setSales] = useState<SaleResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		if (!customerId) return;
		Promise.all([listCustomers(), listSales(Number(customerId))])
			.then(([allCustomers, salesResult]) => {
				setCustomer(allCustomers.find((c) => c.id === Number(customerId)) ?? null);
				setSales(salesResult);
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load purchase history"))
			.finally(() => setLoading(false));
	}, [customerId]);

	if (loading) {
		return (
			<Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
				<CircularProgress />
			</Box>
		);
	}

	if (!customer) {
		return <Alert severity="error">{error ?? "Customer not found"}</Alert>;
	}

	return (
		<Stack spacing={2}>
			<Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
				<Tooltip title="Back to customers">
					<IconButton onClick={() => navigate("/inventory")} aria-label="Back to customers" sx={{ ml: -1 }}>
						<ArrowBackIcon />
					</IconButton>
				</Tooltip>
				<Typography variant="h4" component="h1">
					Purchase history: {customer.name}
				</Typography>
			</Stack>

			{error && <Alert severity="error">{error}</Alert>}

			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					{sales.length === 0 && <Alert severity="info">No sales recorded for this customer yet.</Alert>}
					{sales.length > 0 && (
						<TableContainer>
							<Table size="small">
								<TableHead>
									<TableRow>
										<TableCell>Receipt #</TableCell>
										<TableCell>Date</TableCell>
										<TableCell>Total</TableCell>
										<TableCell>Status</TableCell>
									</TableRow>
								</TableHead>
								<TableBody>
									{sales.map((sale) => (
										<TableRow key={sale.id} hover onClick={() => navigate(`/sales/${sale.id}`)} sx={{ cursor: "pointer" }}>
											<TableCell>{sale.receiptNumber}</TableCell>
											<TableCell>{sale.saleDate}</TableCell>
											<TableCell>{sale.totalAmount}</TableCell>
											<TableCell>
												<Chip label={sale.status} size="small" color={SALE_STATUS_COLOR[sale.status] ?? "default"} />
											</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						</TableContainer>
					)}
				</Stack>
			</Paper>
		</Stack>
	);
}
