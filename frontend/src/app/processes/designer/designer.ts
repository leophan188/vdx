import { Component, ElementRef, inject, signal, viewChild, AfterViewInit, OnDestroy, NgZone } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import BpmnModeler from 'bpmn-js/lib/Modeler';
import { ProcessService } from '../../core/process.service';
import { PositionService, Position } from '../../core/position.service';
import { RoleService, Role } from '../../core/role.service';
import { AuthService, UserAccount } from '../../core/auth.service';
import { OrgService, OrgUnit } from '../../core/org.service';
import { computed } from '@angular/core';
import { FormService, FormSummary } from '../../core/form.service';
import { DocumentService, DocSummary } from '../../core/document.service';
import { ToastService } from '../../shared/toast/toast.service';
import { Modal } from '../../shared/modal/modal';
import { Tabs, TabItem } from '../../shared/tabs/tabs';
import { SearchableSelect, SelectOption } from '../../shared/searchable-select/searchable-select';

type AssigneeType = 'ROLE' | 'POSITION' | 'USER';
type FieldType = 'text' | 'number' | 'date' | 'dropdown' | 'radio' | 'checkbox' | 'richtext';
type OptionSource = 'STATIC' | 'CATALOG';
type RecipientType = 'ASSIGNEE' | 'ROLE' | 'POSITION' | 'USER';
type NotifyListKey = 'emailTo' | 'appTo' | 'cc';

interface FieldDef {
  key: string;
  label: string;
  type: FieldType;
  required?: boolean;
  optionSource?: OptionSource; // dropdown/radio
  options?: string; // STATIC: phân tách dấu phẩy
  catalog?: string; // CATALOG: mã danh mục dùng chung
}

interface Recipient {
  type: RecipientType;
  id?: string;
}

interface NotifyConfig {
  emailTo?: Recipient[];
  appTo?: Recipient[];
  cc?: Recipient[];
  subject?: string;
  content?: string;
}

type FieldPerm = 'EDIT' | 'READONLY' | 'HIDDEN';
type FlowCondOp = 'eq' | 'ne' | 'truthy';

/** Điều kiện trên nhánh (sequence flow) rời gateway — dựa trên dữ liệu form (Story 2.2). */
interface FlowCondition {
  field: string;
  op: FlowCondOp;
  value?: string;
}

interface StepMeta {
  assigneeType?: AssigneeType;
  assigneeId?: string;
  slaHours?: number;
  actions?: string[];
  fields?: FieldDef[];
  notify?: NotifyConfig;
  /** Biểu mẫu gắn vào bước (Story 2.9) + quyền trường theo bước. */
  formId?: string;
  fieldPerms?: Record<string, FieldPerm>;
  /** Key các trường của BƯỚC TRƯỚC được phép SỬA ở bước này. */
  editPriorKeys?: string[];
  /** Bước này có bật soạn thảo tài liệu OnlyOffice (mỗi bước 1 tài liệu riêng gắn hồ sơ). */
  officeDoc?: boolean;
  /** Tài liệu mẫu để load ở bước (copy nội dung làm điểm bắt đầu). Rỗng = trang trắng. */
  officeTemplateId?: string;
  /** Điều kiện chuyển bước (Story 2.2) — chỉ áp cho phần tử SequenceFlow. */
  condition?: FlowCondition;
}

const EMPTY_BPMN = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1"/>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
      <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="StartEvent_1">
        <dc:Bounds x="173" y="102" width="36" height="36"/>
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`;

/** Designer quy trình bpmn-js (Story 2.1): canvas kéo-thả + modal cấu hình bước nhiều tab. */
@Component({
  selector: 'app-designer',
  imports: [FormsModule, Modal, Tabs, SearchableSelect],
  templateUrl: './designer.html'
})
export class Designer implements AfterViewInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private svc = inject(ProcessService);
  private positionSvc = inject(PositionService);
  private roleSvc = inject(RoleService);
  private authSvc = inject(AuthService);
  private orgSvc = inject(OrgService);
  private formSvc = inject(FormService);
  private documentSvc = inject(DocumentService);
  private toast = inject(ToastService);
  private zone = inject(NgZone);

  private readonly canvasRef = viewChild.required<ElementRef<HTMLDivElement>>('canvas');
  private modeler: any;
  private selected: any = null;

  readonly id = this.route.snapshot.paramMap.get('id')!;
  readonly name = signal('');
  readonly saving = signal(false);
  readonly positions = signal<Position[]>([]);
  readonly roles = signal<Role[]>([]);
  readonly users = signal<UserAccount[]>([]);
  readonly units = signal<OrgUnit[]>([]);
  readonly forms = signal<FormSummary[]>([]);
  /** Tài liệu độc lập (không gắn hồ sơ) — dùng làm mẫu để load ở bước OnlyOffice. */
  readonly templateDocs = signal<DocSummary[]>([]);
  /** userId → "chức vụ · bộ phận" (gộp mọi vị trí đang giữ) — hiển thị kèm khi chọn nhân sự. */
  private readonly userInfoById = computed(() => {
    const un = new Map(this.units().map((u) => [u.id, u.name]));
    const map = new Map<string, string>();
    for (const p of this.positions()) {
      if (!p.currentHolderUserId) continue;
      const bits = [p.title, un.get(p.orgUnitId)].filter(Boolean).join(' · ');
      const cur = map.get(p.currentHolderUserId);
      map.set(p.currentHolderUserId, cur ? (bits && !cur.includes(bits) ? cur + '; ' + bits : cur) : bits);
    }
    return map;
  });
  /** Trường của biểu mẫu đang gắn (để cấu hình quyền trường). */
  readonly formFields = signal<{ key: string; label: string }[]>([]);

  readonly selectedId = signal<string | null>(null);
  selectedName = '';
  meta: StepMeta = {};
  private stepsMeta: Record<string, StepMeta> = {};

  // Điều kiện chuyển bước (Story 2.2) — phần tử SequenceFlow đang chọn
  readonly selectedFlowId = signal<string | null>(null);
  readonly flowConfigOpen = signal(false);
  flowCond: FlowCondition = { field: '', op: 'truthy' };
  /** Các trường dữ liệu có thể dùng làm điều kiện (gom từ trường thêm + biểu mẫu gắn bước). */
  readonly dataFields = signal<{ key: string; label: string }[]>([]);
  /** Trường gom THEO TỪNG BƯỚC (để cấu hình cho phép sửa trường bước trước). */
  readonly stepGroups = signal<{ stepKey: string; stepName: string; fields: { key: string; label: string }[] }[]>([]);
  /** Bước đang chọn để xem/tích trường (cấu hình "sửa trường bước trước"). */
  readonly selectedPriorStep = signal<string>('');
  readonly currentPriorGroup = computed(() => this.stepGroups().find((g) => g.stepKey === this.selectedPriorStep()) ?? null);
  readonly COND_OPS: { v: FlowCondOp; l: string }[] = [
    { v: 'truthy', l: 'có giá trị' },
    { v: 'eq', l: 'bằng' },
    { v: 'ne', l: 'khác' }
  ];

  // Kiểm tra cấu hình trước ban hành (Story 5.3 — affordance designer)
  readonly checkOpen = signal(false);
  readonly issues = signal<{ level: 'error' | 'warn'; msg: string }[]>([]);

  // Modal cấu hình bước + tab
  readonly configOpen = signal(false);
  readonly configTab = signal('assignee');
  readonly configTabs: TabItem[] = [
    { key: 'assignee', label: 'Người thực hiện', icon: '👤' },
    { key: 'form', label: 'Biểu mẫu', icon: '📋' },
    { key: 'meta', label: 'Trường thêm', icon: '🧩' },
    { key: 'notify', label: 'Thông báo', icon: '🔔' }
  ];

  readonly PERMS: { v: FieldPerm; l: string }[] = [
    { v: 'EDIT', l: 'Sửa được' },
    { v: 'READONLY', l: 'Chỉ xem' },
    { v: 'HIDDEN', l: 'Ẩn' }
  ];

  readonly ASSIGNEE_TYPES: { v: AssigneeType; l: string }[] = [
    { v: 'ROLE', l: 'Vai trò' },
    { v: 'POSITION', l: 'Chức danh' },
    { v: 'USER', l: 'Nhân sự' }
  ];

  readonly FIELD_TYPES: { v: FieldType; l: string }[] = [
    { v: 'text', l: 'Văn bản' },
    { v: 'number', l: 'Số' },
    { v: 'date', l: 'Ngày' },
    { v: 'dropdown', l: 'Danh sách chọn (dropdown)' },
    { v: 'radio', l: 'Chọn một (radio)' },
    { v: 'checkbox', l: 'Có / Không' },
    { v: 'richtext', l: 'Văn bản định dạng (rich text)' }
  ];

  readonly RECIPIENT_TYPES: { v: RecipientType; l: string }[] = [
    { v: 'ASSIGNEE', l: 'Người thực hiện bước' },
    { v: 'ROLE', l: 'Vai trò' },
    { v: 'POSITION', l: 'Chức danh' },
    { v: 'USER', l: 'Nhân sự' }
  ];

  readonly NOTIFY_LISTS: { key: NotifyListKey; label: string; icon: string }[] = [
    { key: 'emailTo', label: 'Gửi email đến', icon: '✉️' },
    { key: 'appTo', label: 'Thông báo trong app đến', icon: '🔔' },
    { key: 'cc', label: 'CC (đồng gửi)', icon: '📋' }
  ];

  readonly ALL_ACTIONS = [
    { k: 'RECORD', l: 'Ghi lại' }, { k: 'EDIT', l: 'Sửa' }, { k: 'CANCEL', l: 'Hủy' },
    { k: 'SUBMIT', l: 'Trình duyệt' }, { k: 'APPROVE', l: 'Phê duyệt' }, { k: 'RETURN', l: 'Trả lại' },
    { k: 'REJECT', l: 'Từ chối' }, { k: 'DELEGATE', l: 'Uỷ quyền' }
  ];

  /** Biến tiến trình lưu nút hành động đã bấm ở bước trước (backend đặt khi hoàn thành việc). */
  static readonly ACTION_VAR = 'lastAction';
  /** Nhãn hành động theo mã (dùng cho tóm tắt điều kiện nhánh). */
  actionLabelOf(code: string | undefined): string {
    return this.ALL_ACTIONS.find((a) => a.k === code)?.l ?? (code ?? '');
  }
  /** Nhánh này đang rẽ theo NÚT HÀNH ĐỘNG (không phải trường form). */
  isActionCond(): boolean { return this.flowCond.field === Designer.ACTION_VAR; }

  ngAfterViewInit(): void {
    this.modeler = new BpmnModeler({ container: this.canvasRef().nativeElement });
    this.modeler.on('selection.changed', (e: any) =>
      this.zone.run(() => this.onSelect(e.newSelection?.[0] ?? null)));
    this.modeler.on('element.changed', (e: any) =>
      this.zone.run(() => {
        if (this.selected && e.element?.id === this.selected.id) {
          this.selectedName = e.element.businessObject?.name ?? '';
        }
      }));
    // Mở cấu hình khi double-click: bước → cấu hình bước; nhánh → điều kiện chuyển
    this.modeler.on('element.dblclick', (e: any) =>
      this.zone.run(() => {
        if (this.isTask(e.element)) {
          this.onSelect(e.element);
          this.openConfig();
        } else if (this.isFlow(e.element)) {
          this.onSelect(e.element);
          this.openFlowConfig();
        }
      }));
    this.positionSvc.all().subscribe({ next: (p) => this.positions.set(p), error: () => {} });
    this.roleSvc.list().subscribe({ next: (r) => this.roles.set(r), error: () => {} });
    this.authSvc.listUsers().subscribe({ next: (u) => this.users.set(u), error: () => {} });
    this.orgSvc.all().subscribe({ next: (u) => this.units.set(u), error: () => {} });
    this.formSvc.list().subscribe({ next: (f) => this.forms.set(f), error: () => {} });
    // Tài liệu mẫu = tài liệu độc lập (chưa gắn hồ sơ nào) trong màn Tài liệu.
    this.documentSvc.list().subscribe({ next: (d) => this.templateDocs.set(d.filter((x) => !x.instanceId)), error: () => {} });
    this.svc.get(this.id).subscribe({
      next: (p) => {
        this.name.set(p.name);
        this.stepsMeta = p.stepsMetaJson ? JSON.parse(p.stepsMetaJson) : {};
        this.modeler.importXML(p.bpmnXml || EMPTY_BPMN)
          .then(() => this.collectDataFields())
          .catch(() => this.toast.error('Không nạp được sơ đồ'));
      },
      error: () => this.toast.error('Không tải được quy trình')
    });
  }

  ngOnDestroy(): void {
    this.modeler?.destroy();
  }

  private isTask(el: any): boolean {
    return !!el?.type && el.type.includes('Task');
  }
  private isFlow(el: any): boolean {
    return el?.type === 'bpmn:SequenceFlow';
  }

  private onSelect(el: any): void {
    this.selected = el;
    // Nhánh (SequenceFlow): nạp điều kiện
    if (this.isFlow(el)) {
      this.selectedId.set(null);
      this.selectedFlowId.set(el.id);
      const raw: any = this.stepsMeta[el.id] ?? {};
      this.flowCond = raw.condition ? { ...raw.condition } : { field: '', op: 'truthy' };
      return;
    }
    this.selectedFlowId.set(null);
    if (!this.isTask(el)) {
      this.selectedId.set(null);
      return;
    }
    this.selectedId.set(el.id);
    this.selectedName = el.businessObject?.name ?? '';
    const raw: any = this.stepsMeta[el.id] ?? {};
    const m: StepMeta = { actions: [], fields: [], ...raw };
    if (!m.assigneeType) {
      m.assigneeType = 'POSITION';
      if (raw.position) m.assigneeId = raw.position; // tương thích dữ liệu cũ
    }
    if (raw.custom?.length && !raw.fields) {
      m.fields = raw.custom.map((c: any) => ({ key: c.key, label: c.key, type: 'text' as FieldType, optionSource: 'STATIC' as OptionSource, options: '' }));
    }
    if (!m.fields) m.fields = [];
    if (!m.fieldPerms) m.fieldPerms = {};
    if (!m.editPriorKeys) m.editPriorKeys = [];
    m.notify = { emailTo: [], appTo: [], cc: [], subject: '', content: '', ...(raw.notify ?? {}) };
    this.meta = m;
    this.loadFormFields(m.formId);
  }

  // ---- Điều kiện chuyển bước trên nhánh (Story 2.2) ----
  private collectDataFields(): void {
    const map = new Map<string, string>();
    const formIds = new Set<string>();
    for (const m of Object.values(this.stepsMeta)) {
      for (const f of m.fields ?? []) if (f.key) map.set(f.key, f.label || f.key);
      if (m.formId) formIds.add(m.formId);
    }
    this.dataFields.set([...map].map(([key, label]) => ({ key, label })));
    for (const fid of formIds) {
      this.formSvc.get(fid).subscribe({
        next: (form) => {
          try {
            const parsed = form.schemaJson ? JSON.parse(form.schemaJson) : { fields: [] };
            for (const x of parsed.fields ?? []) if (x.key) map.set(x.key, x.label || x.key);
            this.dataFields.set([...map].map(([key, label]) => ({ key, label })));
          } catch {
            /* schema lỗi */
          }
        },
        error: () => {}
      });
    }
  }
  openFlowConfig(): void {
    if (this.selectedFlowId()) this.flowConfigOpen.set(true);
  }
  /** Khi đổi trường điều kiện: nếu chọn "Hành động (nút)" → ép op = bằng + chọn hành động mặc định. */
  onCondFieldChange(): void {
    if (this.isActionCond()) {
      this.flowCond.op = 'eq';
      if (!this.flowCond.value) this.flowCond.value = 'APPROVE';
    }
  }
  condLabel(c: FlowCondition): string {
    // Nhánh rẽ theo nút hành động đã bấm — hiển thị tên hành động tiếng Việt.
    if (c.field === Designer.ACTION_VAR) {
      return `Hành động = "${this.actionLabelOf(c.value)}"`;
    }
    const fl = this.dataFields().find((d) => d.key === c.field)?.label ?? c.field;
    const op = this.COND_OPS.find((o) => o.v === c.op)?.l ?? c.op;
    return c.op === 'truthy' ? `${fl} ${op}` : `${fl} ${op} "${c.value ?? ''}"`;
  }
  saveFlowCondition(): void {
    const id = this.selectedFlowId();
    if (!id) return;
    if (!this.flowCond.field) {
      this.clearFlowCondition();
      return;
    }
    this.stepsMeta[id] = { ...(this.stepsMeta[id] ?? {}), condition: { ...this.flowCond } };
    if (this.selected) {
      this.modeler.get('modeling').updateProperties(this.selected, { name: this.condLabel(this.flowCond) });
    }
    this.flowConfigOpen.set(false);
  }
  clearFlowCondition(): void {
    const id = this.selectedFlowId();
    if (id && this.stepsMeta[id]) delete this.stepsMeta[id].condition;
    if (this.selected) this.modeler.get('modeling').updateProperties(this.selected, { name: '' });
    this.flowCond = { field: '', op: 'truthy' };
    this.flowConfigOpen.set(false);
  }

  // ---- Biểu mẫu gắn vào bước (Story 2.9) ----
  onFormChange(): void {
    this.meta.fieldPerms = {};
    this.loadFormFields(this.meta.formId);
    this.writeMeta();
  }
  private loadFormFields(formId?: string): void {
    if (!formId) {
      this.formFields.set([]);
      return;
    }
    this.formSvc.get(formId).subscribe({
      next: (f) => {
        try {
          const parsed = f.schemaJson ? JSON.parse(f.schemaJson) : { fields: [] };
          this.formFields.set((parsed.fields ?? []).map((x: any) => ({ key: x.key, label: x.label || x.key })));
        } catch {
          this.formFields.set([]);
        }
      },
      error: () => this.formFields.set([])
    });
  }
  /** Sao chép mã trộn «key» vào clipboard để dán vào file mẫu .docx. */
  copyToken(key: string): void {
    const token = '«' + key + '»';
    navigator.clipboard?.writeText(token).then(
      () => this.toast.success('Đã sao chép mã', token),
      () => this.toast.error('Không sao chép được', token)
    );
  }

  fieldPerm(key: string): FieldPerm {
    return this.meta.fieldPerms?.[key] ?? 'EDIT';
  }
  setFieldPerm(key: string, val: FieldPerm): void {
    if (!this.meta.fieldPerms) this.meta.fieldPerms = {};
    this.meta.fieldPerms[key] = val;
    this.writeMeta();
  }

  // ---- Cho phép SỬA trường của bước trước ở bước này ----
  /** Gom trường THEO TỪNG BƯỚC (mọi bước khác bước hiện tại) để cấu hình cho phép sửa. */
  private collectStepGroups(): void {
    const groups: { stepKey: string; stepName: string; fields: { key: string; label: string }[] }[] = [];
    let tasks: any[] = [];
    try { tasks = this.modeler.get('elementRegistry').filter((e: any) => this.isTask(e)); } catch { tasks = []; }
    for (const el of tasks) {
      if (el.id === this.selectedId()) continue; // bỏ chính bước đang cấu hình
      const m: any = this.stepsMeta[el.id] ?? {};
      const g = { stepKey: el.id, stepName: el.businessObject?.name || el.id, fields: [] as { key: string; label: string }[] };
      for (const f of m.fields ?? []) if (f.key) g.fields.push({ key: f.key, label: f.label || f.key });
      groups.push(g);
      if (m.formId) {
        this.formSvc.get(m.formId).subscribe({
          next: (form) => {
            try {
              const parsed = form.schemaJson ? JSON.parse(form.schemaJson) : { fields: [] };
              for (const x of parsed.fields ?? []) if (x.key && x.type !== 'section') g.fields.push({ key: x.key, label: x.label || x.key });
              this.stepGroups.set([...this.stepGroups()]); // phát lại để cập nhật view
            } catch { /* schema lỗi */ }
          },
          error: () => {}
        });
      }
    }
    this.stepGroups.set(groups);
  }
  isPriorEditable(key: string): boolean {
    return (this.meta.editPriorKeys ?? []).includes(key);
  }
  togglePriorEditable(key: string): void {
    const cur = new Set(this.meta.editPriorKeys ?? []);
    cur.has(key) ? cur.delete(key) : cur.add(key);
    this.meta.editPriorKeys = [...cur];
    this.writeMeta();
  }
  /** Trạng thái tích tất cả trường của một bước. */
  allChecked(g: { fields: { key: string }[] }): boolean {
    return g.fields.length > 0 && g.fields.every((f) => this.isPriorEditable(f.key));
  }
  toggleStepAll(g: { fields: { key: string }[] }): void {
    const cur = new Set(this.meta.editPriorKeys ?? []);
    const all = this.allChecked(g);
    for (const f of g.fields) { all ? cur.delete(f.key) : cur.add(f.key); }
    this.meta.editPriorKeys = [...cur];
    this.writeMeta();
  }
  /** Số trường đã tích của một bước (để nhắc trong dropdown). */
  countChecked(g: { fields: { key: string }[] }): number {
    return g.fields.filter((f) => this.isPriorEditable(f.key)).length;
  }

  openConfig(): void {
    if (this.selectedId()) {
      this.collectDataFields(); // trường toàn quy trình (điều kiện nhánh)
      this.collectStepGroups(); // trường gom theo bước (cho phép sửa bước trước)
      this.selectedPriorStep.set('');
      this.configTab.set('assignee');
      this.configOpen.set(true);
    }
  }

  // ---- Phân công ----
  assigneeOptions(): { id: string; label: string }[] {
    return this.targetOptions(this.meta.assigneeType ?? 'POSITION');
  }
  assigneeTypeLabel(): string {
    const t = this.meta.assigneeType ?? 'POSITION';
    return t === 'ROLE' ? 'vai trò' : t === 'USER' ? 'nhân sự' : 'chức danh';
  }
  onAssigneeTypeChange(): void {
    this.meta.assigneeId = undefined;
    this.writeMeta();
  }
  /** Options cho ô tìm-kiếm-chọn người thực hiện (typeahead) — kèm chức vụ · bộ phận khi là nhân sự. */
  assigneeSelOptions(): SelectOption[] {
    return this.withInfo(this.meta.assigneeType ?? 'POSITION', this.assigneeOptions());
  }
  /** Gắn dòng phụ "chức vụ · bộ phận" cho loại USER (không dấu tìm được cả dòng phụ). */
  private withInfo(type: RecipientType | AssigneeType, opts: { id: string; label: string }[]): SelectOption[] {
    if (type !== 'USER') {
      return opts.map((o) => ({ value: o.id, label: o.label }));
    }
    const info = this.userInfoById();
    return opts.map((o) => ({ value: o.id, label: o.label, sub: info.get(o.id) || undefined }));
  }
  onAssigneePick(v: string): void {
    this.meta.assigneeId = v || undefined;
    this.writeMeta();
  }
  /** Options cho ô tìm-kiếm-chọn người nhận thông báo theo loại. */
  recipientSelOptions(type: RecipientType): SelectOption[] {
    return this.withInfo(type, this.targetOptions(type));
  }
  onRecipientPick(r: Recipient, v: string): void {
    r.id = v || undefined;
    this.writeMeta();
  }

  // ---- Metadata ----
  isChoice(t?: FieldType): boolean {
    return t === 'dropdown' || t === 'radio';
  }
  addField(): void {
    this.meta.fields = [...(this.meta.fields ?? []), { key: '', label: '', type: 'text', optionSource: 'STATIC', options: '' }];
    this.writeMeta();
  }
  removeField(i: number): void {
    this.meta.fields = (this.meta.fields ?? []).filter((_, idx) => idx !== i);
    this.writeMeta();
  }

  // ---- Thông báo ----
  private ensureNotify(): NotifyConfig {
    if (!this.meta.notify) this.meta.notify = { emailTo: [], appTo: [], cc: [] };
    return this.meta.notify;
  }
  recipients(key: NotifyListKey): Recipient[] {
    return this.ensureNotify()[key] ?? [];
  }
  addRecipient(key: NotifyListKey): void {
    const n = this.ensureNotify();
    n[key] = [...(n[key] ?? []), { type: 'ASSIGNEE' }];
    this.writeMeta();
  }
  removeRecipient(key: NotifyListKey, i: number): void {
    const n = this.ensureNotify();
    n[key] = (n[key] ?? []).filter((_, idx) => idx !== i);
    this.writeMeta();
  }
  /** Danh sách đối tượng theo loại (vai trò/vị trí/người); ASSIGNEE không cần chọn cụ thể. */
  targetOptions(type: RecipientType | AssigneeType): { id: string; label: string }[] {
    switch (type) {
      case 'ROLE':
        return this.roles().map((r) => ({ id: r.code, label: `${r.name} (${r.code})` }));
      case 'USER':
        return this.users().map((u) => ({ id: u.id, label: `${u.fullName} (${u.username})` }));
      case 'POSITION':
        return this.positions().map((p) => ({ id: p.id, label: p.title }));
      default:
        return [];
    }
  }

  updateName(): void {
    if (this.selected) {
      this.modeler.get('modeling').updateProperties(this.selected, { name: this.selectedName });
    }
  }

  writeMeta(): void {
    const id = this.selectedId();
    if (id) {
      this.stepsMeta[id] = { ...this.meta };
    }
  }

  hasAction(k: string): boolean {
    return (this.meta.actions ?? []).includes(k);
  }
  toggleAction(k: string): void {
    const set = new Set(this.meta.actions ?? []);
    set.has(k) ? set.delete(k) : set.add(k);
    this.meta.actions = [...set];
    this.writeMeta();
  }

  /** Kiểm tra cấu hình quy trình trước khi ban hành (Story 5.3): chỉ ra lỗi/cảnh báo. */
  validate(): void {
    const reg = this.modeler.get('elementRegistry');
    const out: { level: 'error' | 'warn'; msg: string }[] = [];
    let userTasks = 0;
    let hasStart = false;
    let hasEnd = false;
    for (const el of reg.getAll()) {
      const t = el.type;
      const name = el.businessObject?.name || el.id;
      if (t === 'bpmn:StartEvent') hasStart = true;
      if (t === 'bpmn:EndEvent') hasEnd = true;
      if (t === 'bpmn:UserTask') {
        userTasks++;
        const m = this.stepsMeta[el.id];
        if (!m || !m.assigneeType || !m.assigneeId) {
          out.push({ level: 'error', msg: `Bước "${name}" chưa gán người thực hiện.` });
        }
        if (m && (!m.actions || !m.actions.length)) {
          out.push({ level: 'warn', msg: `Bước "${name}" chưa khai báo hành động.` });
        }
      }
      if (t === 'bpmn:ExclusiveGateway' || t === 'bpmn:InclusiveGateway') {
        const outgoing = el.businessObject?.outgoing || [];
        if (outgoing.length > 1) {
          const withCond = outgoing.filter((f: any) => this.stepsMeta[f.id]?.condition).length;
          if (withCond === 0) {
            out.push({ level: 'warn', msg: `Cổng rẽ nhánh "${name}" chưa đặt điều kiện cho nhánh nào (sẽ đi nhánh mặc định).` });
          }
        }
      }
    }
    if (!hasStart) out.push({ level: 'error', msg: 'Thiếu sự kiện Bắt đầu.' });
    if (!hasEnd) out.push({ level: 'error', msg: 'Thiếu sự kiện Kết thúc.' });
    if (userTasks === 0) out.push({ level: 'error', msg: 'Quy trình chưa có bước thực hiện nào.' });
    this.issues.set(out);
    this.checkOpen.set(true);
  }

  errorCount(): number { return this.issues().filter((i) => i.level === 'error').length; }

  async save(): Promise<void> {
    this.saving.set(true);
    try {
      const { xml } = await this.modeler.saveXML({ format: true });
      for (const m of Object.values(this.stepsMeta)) {
        if (m.fields) m.fields = m.fields.filter((f) => f.key.trim());
      }
      this.svc.saveDesign(this.id, xml, JSON.stringify(this.stepsMeta)).subscribe({
        next: () => {
          this.toast.success('Đã lưu quy trình', this.name());
          this.saving.set(false);
        },
        error: () => {
          this.toast.error('Không lưu được quy trình');
          this.saving.set(false);
        }
      });
    } catch {
      this.toast.error('Không xuất được sơ đồ');
      this.saving.set(false);
    }
  }

  back(): void {
    this.router.navigate(['/processes']);
  }
}
