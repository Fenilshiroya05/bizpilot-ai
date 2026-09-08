#!/usr/bin/env node
/**
 * BizPilot AI — Phase 22.5 local demo-data seed.
 *
 * Populates Customers, Leads, Product Categories, Products, and Quotations
 * for the local demo organization (created by the backend's own
 * `DemoUsersSeeder`, gated by `bizpilot.seed.demo-users=true` under the
 * `local` Spring profile — see backend/src/main/java/com/bizpilot/devseed).
 *
 * This script speaks ONLY the real public REST API, authenticated as the
 * demo OWNER account, exactly as a real frontend client would — it never
 * touches the database directly and never fabricates a calculated total:
 * every Quotation's subtotal/discountAmount/taxAmount/grandTotal and every
 * line's lineSubtotal/lineTaxAmount/unitPrice/taxPercentage snapshot comes
 * back from the backend's own response.
 *
 * Idempotent: re-running this script does not create duplicate customers,
 * leads, categories, or products (matched by name/SKU before creating), and
 * skips the whole quotations batch if any quotation already exists for this
 * organization.
 *
 * Usage:
 *   BIZPILOT_API_URL=http://localhost:8081 node scripts/seed-demo-data.mjs
 *   (defaults to http://localhost:8080 if BIZPILOT_API_URL is unset)
 *
 * Requires the backend to already be running with the demo users seeded.
 */

const BASE_URL = process.env.BIZPILOT_API_URL || 'http://localhost:8080';
const OWNER_EMAIL = 'owner@bizpilot.local';
const OWNER_PASSWORD = 'Passw0rd1';

async function api(path, { method = 'GET', body, token } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (res.status === 204) return null;
  const text = await res.text();
  const json = text ? JSON.parse(text) : null;
  if (!res.ok) {
    throw new Error(`${method} ${path} -> ${res.status}: ${JSON.stringify(json)}`);
  }
  return json;
}

async function login(email, password) {
  const res = await api('/api/v1/auth/login', { method: 'POST', body: { email, password } });
  return res.accessToken;
}

// ---------------------------------------------------------------------------
// Idempotent "ensure" helpers — search first, create only if missing.
// ---------------------------------------------------------------------------

async function ensureCategory(token, name) {
  const page = await api(`/api/v1/products/categories?size=100`, { token });
  const existing = page.content.find((c) => c.name === name);
  if (existing) return existing;
  return api('/api/v1/products/categories', { method: 'POST', token, body: { name } });
}

async function ensureProduct(token, categoryId, spec) {
  const page = await api(`/api/v1/products?q=${encodeURIComponent(spec.sku)}&size=100`, { token });
  const existing = page.content.find((p) => p.sku === spec.sku);
  if (existing) return existing;
  return api('/api/v1/products', {
    method: 'POST',
    token,
    body: {
      sku: spec.sku,
      name: spec.name,
      description: spec.description,
      unit: spec.unit,
      price: spec.price,
      taxPercentage: spec.taxPercentage,
      categoryId,
    },
  });
}

async function deactivateProduct(token, id) {
  return api(`/api/v1/products/${id}`, { method: 'DELETE', token });
}

async function ensureCustomer(token, spec) {
  const page = await api(`/api/v1/customers?q=${encodeURIComponent(spec.name)}&size=100`, { token });
  const existing = page.content.find((c) => c.name === spec.name);
  if (existing) return existing;
  return api('/api/v1/customers', { method: 'POST', token, body: spec });
}

async function addCustomerNote(token, customerId, content) {
  return api(`/api/v1/customers/${customerId}/notes`, { method: 'POST', token, body: { content } });
}

async function ensureLead(token, spec) {
  const page = await api(`/api/v1/leads?q=${encodeURIComponent(spec.name)}&size=100`, { token });
  const existing = page.content.find((l) => l.name === spec.name);
  if (existing) return existing;
  const { assigneeUserId, note, ...createBody } = spec;
  const lead = await api('/api/v1/leads', { method: 'POST', token, body: createBody });
  if (assigneeUserId) {
    await api(`/api/v1/leads/${lead.id}/assign`, { method: 'POST', token, body: { assigneeUserId } });
  }
  if (note) {
    await api(`/api/v1/leads/${lead.id}/notes`, { method: 'POST', token, body: { content: note } });
  }
  return lead;
}

// ---------------------------------------------------------------------------
// Demo data — fixed and deterministic, clearly synthetic Indian SMB names.
// ---------------------------------------------------------------------------

const CATEGORY_NAMES = ['Electrical', 'Mechanical', 'Hardware', 'Automotive', 'Industrial Equipment'];

const CUSTOMERS = [
  { name: 'Shree Enterprise', company: 'Shree Enterprise', email: 'contact@shreeenterprise.example', phone: '+91 98200 11111', address: 'Ring Road, Surat, Gujarat', gstin: '24AAAPL2130H1Z5', notes: 'Long-standing bulk hardware buyer.' },
  { name: 'Patel Industries', company: 'Patel Industries Pvt Ltd', email: 'sales@patelindustries.example', phone: '+91 98200 22222', address: 'GIDC Vatva, Ahmedabad, Gujarat', gstin: '24PQRST5678G2Z9', notes: null },
  { name: 'Sunrise Electronics', company: 'Sunrise Electronics', email: 'info@sunriseelectronics.example', phone: '+91 98200 33333', address: 'MG Road, Rajkot, Gujarat', gstin: null, notes: 'Prefers quotations with 30-day validity.' },
  { name: 'Royal Furniture', company: 'Royal Furniture Works', email: 'orders@royalfurniture.example', phone: '+91 98200 44444', address: 'Station Road, Vadodara, Gujarat', gstin: null, notes: null },
  { name: 'Gujarat Auto Parts', company: 'Gujarat Auto Parts Co', email: 'parts@gujaratauto.example', phone: '+91 98200 55555', address: 'Odhav Industrial Estate, Ahmedabad, Gujarat', gstin: '24GAPCO9988K3Z1', notes: null },
  { name: 'Ahmedabad Traders', company: 'Ahmedabad Traders', email: 'contact@ahmedabadtraders.example', phone: '+91 98200 66666', address: 'Manek Chowk, Ahmedabad, Gujarat', gstin: null, notes: null },
  { name: 'Shakti Engineering', company: 'Shakti Engineering Works', email: 'info@shaktieng.example', phone: '+91 98200 77777', address: 'Phase 2, GIDC Naroda, Ahmedabad', gstin: '24SHAKT4321E4Z7', notes: 'Repeat customer — industrial equipment.' },
  { name: 'Prime Automation', company: 'Prime Automation Systems', email: 'sales@primeauto.example', phone: '+91 98200 88888', address: 'Sarkhej-Gandhinagar Highway, Ahmedabad', gstin: null, notes: null },
  { name: 'Rajesh Hardware', company: 'Rajesh Hardware Store', email: 'rajesh@rajeshhardware.example', phone: '+91 98200 99999', address: 'Bhadra Fort Road, Ahmedabad', gstin: null, notes: null },
  { name: 'Maruti Industrial Supplies', company: 'Maruti Industrial Supplies', email: 'contact@marutiindustrial.example', phone: '+91 98201 00000', address: 'CTM Cross Road, Ahmedabad', gstin: '24MARUT6543S5Z2', notes: null },
  { name: 'Om Sai Enterprises', company: 'Om Sai Enterprises', email: 'omsai@omsaienterprises.example', phone: '+91 98201 11122', address: 'Nikol, Ahmedabad, Gujarat', gstin: null, notes: 'New customer — first quotation pending.' },
  { name: 'Krishna Metal Works', company: 'Krishna Metal Works', email: 'krishna@krishnametal.example', phone: '+91 98201 22233', address: 'Vatva GIDC, Ahmedabad, Gujarat', gstin: null, notes: null },
];

const LEAD_SOURCES = ['WEBSITE', 'REFERRAL', 'SOCIAL_MEDIA', 'EMAIL', 'PHONE', 'OTHER'];
const LEAD_STATUSES_AFTER_CREATE = ['CONTACTED', 'QUALIFIED', 'PROPOSAL', 'NEGOTIATION', 'WON', 'LOST'];

function buildLeads(ownerId, salesId, employeeId) {
  const specs = [
    { name: 'Bharat Steel Traders', company: 'Bharat Steel Traders', source: 'WEBSITE', priority: 'HIGH', followUpDate: '2026-09-15', assigneeUserId: salesId },
    { name: 'Nilesh Kapoor', company: 'Kapoor Electricals', email: 'nilesh@kapoorelectricals.example', source: 'REFERRAL', priority: 'MEDIUM', followUpDate: '2026-09-20', assigneeUserId: employeeId },
    { name: 'Modern Tools Co', company: 'Modern Tools Co', source: 'SOCIAL_MEDIA', priority: 'LOW', followUpDate: null },
    { name: 'Vikas Mehta', company: 'Mehta Auto Garage', phone: '+91 90000 10001', source: 'PHONE', priority: 'MEDIUM', followUpDate: '2026-09-10', assigneeUserId: salesId },
    { name: 'Gokul Industries', company: 'Gokul Industries', source: 'EMAIL', priority: 'HIGH', followUpDate: '2026-09-05', assigneeUserId: ownerId, note: 'Requested a call back about bulk bearing order.' },
    { name: 'Sanjay Rane', company: 'Rane Fabrication', source: 'OTHER', priority: 'LOW', followUpDate: null },
    { name: 'Deepak Solanki', company: 'Solanki Hardware Mart', source: 'WEBSITE', priority: 'MEDIUM', followUpDate: '2026-09-25' },
    { name: 'Star Industrial Corp', company: 'Star Industrial Corp', source: 'REFERRAL', priority: 'HIGH', followUpDate: '2026-09-08', assigneeUserId: employeeId },
    { name: 'Anita Joshi', company: 'Joshi Electricals', email: 'anita@joshielectricals.example', source: 'SOCIAL_MEDIA', priority: 'MEDIUM', followUpDate: null },
    { name: 'Bright Automation Ltd', company: 'Bright Automation Ltd', source: 'EMAIL', priority: 'LOW', followUpDate: '2026-10-01' },
    { name: 'Kiran Desai', company: 'Desai Metal Traders', source: 'PHONE', priority: 'MEDIUM', followUpDate: '2026-09-12', assigneeUserId: salesId },
    { name: 'Unity Engineering Works', company: 'Unity Engineering Works', source: 'OTHER', priority: 'HIGH', followUpDate: '2026-09-06' },
    { name: 'Ramesh Iyer', company: 'Iyer Auto Spares', source: 'WEBSITE', priority: 'LOW', followUpDate: null, assigneeUserId: employeeId },
    { name: 'National Hardware Supply', company: 'National Hardware Supply', source: 'REFERRAL', priority: 'MEDIUM', followUpDate: '2026-09-18' },
    { name: 'Pooja Nair', company: 'Nair Electricals', email: 'pooja@nairelectricals.example', source: 'SOCIAL_MEDIA', priority: 'HIGH', followUpDate: '2026-09-09', assigneeUserId: ownerId },
    { name: 'Trident Industrial Equipment', company: 'Trident Industrial Equipment', source: 'EMAIL', priority: 'MEDIUM', followUpDate: null },
    { name: 'Ashok Bhatt', company: 'Bhatt Auto Works', source: 'PHONE', priority: 'LOW', followUpDate: '2026-09-30' },
    { name: 'Global Fasteners Inc', company: 'Global Fasteners Inc', source: 'OTHER', priority: 'HIGH', followUpDate: '2026-09-07', assigneeUserId: salesId },
  ];
  // A subset moves past NEW after creation, to demonstrate every status.
  const statusOverrides = new Map([
    ['Bharat Steel Traders', 'CONTACTED'],
    ['Nilesh Kapoor', 'QUALIFIED'],
    ['Vikas Mehta', 'PROPOSAL'],
    ['Gokul Industries', 'NEGOTIATION'],
    ['Star Industrial Corp', 'WON'],
    ['Kiran Desai', 'LOST'],
    ['Unity Engineering Works', 'CONTACTED'],
    ['Pooja Nair', 'QUALIFIED'],
  ]);
  return specs.map((s) => ({ ...s, statusOverride: statusOverrides.get(s.name) }));
}

const PRODUCTS = [
  // Electrical
  { sku: 'ELEC-001', name: 'LED Industrial Light 100W', description: 'High-bay LED fixture for warehouse/factory floors.', unit: 'pcs', price: 1250.0, taxPercentage: 18, category: 'Electrical' },
  { sku: 'ELEC-002', name: 'Industrial Relay 24V', description: '24V DC industrial control relay.', unit: 'pcs', price: 180.0, taxPercentage: 18, category: 'Electrical' },
  { sku: 'ELEC-003', name: 'Control Panel Enclosure', description: 'IP65 sheet-metal control panel enclosure.', unit: 'pcs', price: 4500.0, taxPercentage: 18, category: 'Electrical' },
  { sku: 'ELEC-004', name: 'Electric Motor 5HP', description: '3-phase induction motor, 5HP.', unit: 'pcs', price: 12500.0, taxPercentage: 18, category: 'Electrical' },
  { sku: 'ELEC-005', name: 'MCB Switch 32A', description: 'Miniature circuit breaker, 32A, single pole.', unit: 'pcs', price: 220.0, taxPercentage: 18, category: 'Electrical' },
  { sku: 'ELEC-006', name: 'Copper Wire 2.5mm (100m)', description: 'PVC-insulated copper wire, 2.5mm, 100m coil.', unit: 'coil', price: 2100.0, taxPercentage: 18, category: 'Electrical' },
  { sku: 'ELEC-007', name: 'Distribution Board 8-Way', description: '8-way SPN distribution board.', unit: 'pcs', price: 950.0, taxPercentage: 18, category: 'Electrical' },
  // Mechanical
  { sku: 'MECH-001', name: 'Industrial Bearing 6205', description: 'Deep groove ball bearing, 6205 series.', unit: 'pcs', price: 320.0, taxPercentage: 18, category: 'Mechanical' },
  { sku: 'MECH-002', name: 'Hydraulic Pump 2HP', description: 'Gear-type hydraulic pump, 2HP rated.', unit: 'pcs', price: 8900.0, taxPercentage: 18, category: 'Mechanical' },
  { sku: 'MECH-003', name: 'Pressure Gauge 0-10 Bar', description: 'Dial pressure gauge, bottom connection.', unit: 'pcs', price: 450.0, taxPercentage: 18, category: 'Mechanical' },
  { sku: 'MECH-004', name: 'Gear Coupling 50mm', description: 'Flexible gear coupling, 50mm bore.', unit: 'pcs', price: 1600.0, taxPercentage: 18, category: 'Mechanical' },
  { sku: 'MECH-005', name: 'Timing Belt B-Series', description: 'Rubber timing belt, B-series profile.', unit: 'pcs', price: 380.0, taxPercentage: 18, category: 'Mechanical' },
  { sku: 'MECH-006', name: 'Chain Sprocket 40T', description: 'Steel chain sprocket, 40 teeth.', unit: 'pcs', price: 720.0, taxPercentage: 18, category: 'Mechanical' },
  // Hardware
  { sku: 'HW-001', name: 'SS Bolt M10x50', description: 'Stainless steel hex bolt, M10x50.', unit: 'pcs', price: 12.5, taxPercentage: 12, category: 'Hardware' },
  { sku: 'HW-002', name: 'Stainless Steel Washer M10', description: 'SS flat washer, M10.', unit: 'pcs', price: 2.5, taxPercentage: 12, category: 'Hardware' },
  { sku: 'HW-003', name: 'Hex Nut M12', description: 'Steel hex nut, M12, zinc-plated.', unit: 'pcs', price: 4.0, taxPercentage: 12, category: 'Hardware' },
  { sku: 'HW-004', name: 'Anchor Fastener 8mm', description: 'Expansion anchor fastener, 8mm.', unit: 'pcs', price: 8.0, taxPercentage: 12, category: 'Hardware' },
  { sku: 'HW-005', name: 'Wing Nut M8', description: 'Steel wing nut, M8.', unit: 'pcs', price: 5.5, taxPercentage: 12, category: 'Hardware' },
  { sku: 'HW-006', name: 'Threaded Rod M10 (1m)', description: 'Zinc-plated threaded rod, M10, 1 metre.', unit: 'pcs', price: 95.0, taxPercentage: 12, category: 'Hardware' },
  // Automotive
  { sku: 'AUTO-001', name: 'Automotive Brake Pad Set', description: 'Front brake pad set, ceramic compound.', unit: 'set', price: 1450.0, taxPercentage: 28, category: 'Automotive' },
  { sku: 'AUTO-002', name: 'Clutch Plate Assembly', description: 'Single-plate clutch assembly, commercial vehicle.', unit: 'pcs', price: 3200.0, taxPercentage: 28, category: 'Automotive' },
  { sku: 'AUTO-003', name: 'Radiator Fan Assembly', description: 'Electric radiator cooling fan, 12V.', unit: 'pcs', price: 2100.0, taxPercentage: 28, category: 'Automotive' },
  { sku: 'AUTO-004', name: 'Shock Absorber Rear', description: 'Gas-charged rear shock absorber.', unit: 'pcs', price: 1850.0, taxPercentage: 28, category: 'Automotive' },
  { sku: 'AUTO-005', name: 'Air Filter Cartridge', description: 'Paper element air filter cartridge.', unit: 'pcs', price: 380.0, taxPercentage: 28, category: 'Automotive' },
  { sku: 'AUTO-006', name: 'Brake Disc Rotor', description: 'Ventilated front brake disc rotor.', unit: 'pcs', price: 2650.0, taxPercentage: 28, category: 'Automotive' },
  // Industrial Equipment
  { sku: 'IND-001', name: 'Air Compressor 5HP', description: 'Reciprocating air compressor, 5HP, 90L tank.', unit: 'pcs', price: 28500.0, taxPercentage: 18, category: 'Industrial Equipment' },
  { sku: 'IND-002', name: 'Conveyor Roller 75mm', description: 'Galvanized steel conveyor roller, 75mm dia.', unit: 'pcs', price: 650.0, taxPercentage: 18, category: 'Industrial Equipment' },
  { sku: 'IND-003', name: 'Industrial Safety Helmet', description: 'ISI-marked safety helmet, ratchet adjustable.', unit: 'pcs', price: 220.0, taxPercentage: 18, category: 'Industrial Equipment' },
  { sku: 'IND-004', name: 'Welding Machine 200A', description: 'Inverter ARC welding machine, 200A.', unit: 'pcs', price: 9800.0, taxPercentage: 18, category: 'Industrial Equipment' },
];

// SKUs deliberately deactivated after creation to demonstrate INACTIVE state
// and the "inactive products cannot be added to a quotation" rule.
const INACTIVE_SKUS = ['ELEC-007', 'MECH-006', 'HW-006', 'AUTO-006', 'IND-002'];

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

async function main() {
  console.log(`[seed] Target API: ${BASE_URL}`);
  const token = await login(OWNER_EMAIL, OWNER_PASSWORD);
  console.log('[seed] Logged in as owner@bizpilot.local');

  const me = await api('/api/v1/auth/me', { token });
  const users = {};
  for (const email of ['owner@bizpilot.local', 'sales@bizpilot.local', 'employee@bizpilot.local']) {
    const password = 'Passw0rd1';
    const t = await login(email, password);
    const u = await api('/api/v1/auth/me', { token: t });
    users[email] = u.id;
  }

  // --- Categories ---
  const categories = {};
  for (const name of CATEGORY_NAMES) {
    const cat = await ensureCategory(token, name);
    categories[name] = cat.id;
  }
  console.log(`[seed] Categories ready: ${Object.keys(categories).length}`);

  // --- Products ---
  const products = [];
  for (const spec of PRODUCTS) {
    const product = await ensureProduct(token, categories[spec.category], spec);
    products.push(product);
  }
  for (const sku of INACTIVE_SKUS) {
    const product = products.find((p) => p.sku === sku);
    if (product && product.status === 'ACTIVE') {
      await deactivateProduct(token, product.id);
      product.status = 'INACTIVE';
    }
  }
  console.log(`[seed] Products ready: ${products.length} (${INACTIVE_SKUS.length} deactivated)`);

  // --- Customers ---
  const customers = [];
  for (const spec of CUSTOMERS) {
    const customer = await ensureCustomer(token, spec);
    customers.push(customer);
  }
  // A couple of activity notes, distinct from the static `notes` field.
  await addCustomerNote(token, customers[0].id, 'Called to confirm delivery address for the last order.');
  await addCustomerNote(token, customers[6].id, 'Discussed volume discount for next quarter.');
  console.log(`[seed] Customers ready: ${customers.length}`);

  // --- Leads ---
  const leadSpecs = buildLeads(users['owner@bizpilot.local'], users['sales@bizpilot.local'], users['employee@bizpilot.local']);
  const leads = [];
  for (const spec of leadSpecs) {
    const { statusOverride, ...createSpec } = spec;
    const lead = await ensureLead(token, createSpec);
    if (statusOverride && lead.status !== statusOverride) {
      await api(`/api/v1/leads/${lead.id}`, { method: 'PATCH', token, body: { status: statusOverride } });
    }
    leads.push(lead);
  }
  console.log(`[seed] Leads ready: ${leads.length}`);

  // --- Quotations (skip entirely if any already exist for this org) ---
  const existingQuotations = await api('/api/v1/quotations?size=1', { token });
  let quotationItemCount = 0;
  let quotationCount = existingQuotations.totalElements;
  if (existingQuotations.totalElements > 0) {
    console.log(`[seed] Quotations already exist (${existingQuotations.totalElements}) — skipping quotation batch.`);
  } else {
    const activeProducts = products.filter((p) => p.status === 'ACTIVE');
    const pick = (i) => activeProducts[i % activeProducts.length];

    const quotationSpecs = [
      { customer: customers[0], discountPercentage: 0, validUntil: '2026-10-15', items: [{ product: pick(0), quantity: 5 }, { product: pick(7), quantity: 20 }] },
      { customer: customers[1], discountPercentage: 5, validUntil: '2026-10-01', items: [{ product: pick(1), quantity: 2 }] },
      { customer: customers[2], discountPercentage: 10, validUntil: '2026-09-20', items: [{ product: pick(2), quantity: 1 }, { product: pick(3), quantity: 1 }, { product: pick(4), quantity: 4 }] },
      { customer: customers[3], discountPercentage: 0, validUntil: '2026-10-30', items: [{ product: pick(13), quantity: 100 }, { product: pick(14), quantity: 100 }] },
      { customer: customers[4], discountPercentage: 15, validUntil: '2026-09-10', items: [{ product: pick(18), quantity: 4 }], status: 'SENT' },
      { customer: customers[5], discountPercentage: 0, validUntil: '2026-09-25', items: [{ product: pick(20), quantity: 2 }, { product: pick(22), quantity: 6 }], status: 'SENT' },
      { customer: customers[6], discountPercentage: 5, validUntil: '2026-10-05', items: [{ product: pick(25), quantity: 1 }], status: 'ACCEPTED' },
      { customer: customers[7], discountPercentage: 0, validUntil: '2026-09-18', items: [{ product: pick(8), quantity: 3 }, { product: pick(9), quantity: 6 }], status: 'ACCEPTED' },
      { customer: customers[8], discountPercentage: 0, validUntil: '2026-08-15', items: [{ product: pick(10), quantity: 2 }], status: 'REJECTED' },
      { customer: customers[9], discountPercentage: 10, validUntil: '2026-08-01', items: [{ product: pick(15), quantity: 50 }, { product: pick(16), quantity: 50 }, { product: pick(17), quantity: 50 }], status: 'EXPIRED' },
      { customer: customers[10], discountPercentage: 0, validUntil: '2026-08-10', items: [{ product: pick(23), quantity: 1 }], status: 'EXPIRED' },
      { customer: customers[11], discountPercentage: 0, validUntil: '2026-10-20', items: [{ product: pick(5), quantity: 10 }], status: 'CANCELLED' },
      { customer: customers[0], discountPercentage: 5, validUntil: '2026-10-25', items: [{ product: pick(19), quantity: 2 }, { product: pick(24), quantity: 1 }], status: 'CANCELLED' },
    ];

    for (const spec of quotationSpecs) {
      const created = await api('/api/v1/quotations', {
        method: 'POST',
        token,
        body: {
          customerId: spec.customer.id,
          validUntil: spec.validUntil,
          discountPercentage: spec.discountPercentage,
          items: spec.items.map((i) => ({ productId: i.product.id, quantity: i.quantity })),
        },
      });
      quotationItemCount += created.items.length;
      if (spec.status === 'CANCELLED') {
        await api(`/api/v1/quotations/${created.id}`, { method: 'DELETE', token });
      } else if (spec.status) {
        await api(`/api/v1/quotations/${created.id}`, { method: 'PATCH', token, body: { status: spec.status } });
      }
    }
    quotationCount = quotationSpecs.length;
    console.log(`[seed] Quotations created: ${quotationCount} (${quotationItemCount} line items)`);
  }

  console.log('\n[seed] Summary:');
  console.log(`  Categories:  ${Object.keys(categories).length}`);
  console.log(`  Products:    ${products.length}`);
  console.log(`  Customers:   ${customers.length}`);
  console.log(`  Leads:       ${leads.length}`);
  console.log(`  Quotations:  ${quotationCount}`);
  console.log('[seed] Done.');
}

main().catch((err) => {
  console.error('[seed] FAILED:', err.message);
  process.exit(1);
});
