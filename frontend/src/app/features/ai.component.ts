import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { apiBase } from '../core/api';

@Component({
  selector: 'app-ai',
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="min-h-[calc(100vh-4rem)] bg-slate-50 px-4 py-12 sm:px-6 lg:py-20 relative">
      <div class="mx-auto max-w-3xl">
        <div class="mb-8 text-center">
          <span class="mb-4 inline-flex items-center rounded-full border border-blue-100 bg-blue-50 px-4 py-1.5 text-xs font-bold uppercase tracking-[0.18em] text-blue-800">
            Inteligencia Artificial
          </span>
          <h1 class="text-3xl font-bold tracking-tight text-slate-900 sm:text-4xl">
            Asistente de Carreras
          </h1>
          <p class="mx-auto mt-4 max-w-2xl text-sm leading-7 text-slate-500 sm:text-base">
            Cuéntanos qué quieres aprender o muéstranos tu experiencia. Nuestra IA te guiará hacia el mejor camino.
          </p>
        </div>

        <div class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm sm:p-10">
          <div class="space-y-4">
            <label class="block text-sm font-semibold text-slate-800">
              ¿Cuál es tu objetivo o experiencia?
            </label>

            <!-- Text Area -->
            <textarea
              [(ngModel)]="goal"
              rows="4"
              [disabled]="!!selectedFile || recording"
              placeholder="Ej: Quiero prepararme para una entrevista backend Java..."
              class="w-full resize-y rounded-xl border border-slate-300 bg-white px-4 py-3 text-sm text-slate-800 outline-none transition duration-200 placeholder:text-slate-400 hover:border-slate-400 focus:border-blue-800 focus:ring-4 focus:ring-blue-800/10 disabled:bg-slate-50 disabled:text-slate-400"
            ></textarea>

            <!-- Multimedia controls -->
            <div class="flex flex-wrap items-center gap-3">
              <button 
                type="button" 
                (click)="toggleRecording()" 
                [class.bg-red-50]="recording"
                [class.text-red-700]="recording"
                [class.border-red-200]="recording"
                [class.animate-pulse]="recording"
                class="inline-flex items-center gap-2 rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-slate-200"
              >
                @if (recording) {
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="w-5 h-5 text-red-600">
                    <path fill-rule="evenodd" d="M4.5 7.5a3 3 0 0 1 3-3h9a3 3 0 0 1 3 3v9a3 3 0 0 1-3 3h-9a3 3 0 0 1-3-3v-9Z" clip-rule="evenodd" />
                  </svg>
                  Detener grabación
                } @else {
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="w-5 h-5 text-blue-600">
                    <path d="M8.25 4.5a3.75 3.75 0 1 1 7.5 0v8.25a3.75 3.75 0 1 1-7.5 0V4.5Z" />
                    <path d="M6 10.5a.75.75 0 0 1 .75.75v1.5a5.25 5.25 0 1 0 10.5 0v-1.5a.75.75 0 0 1 1.5 0v1.5a6.751 6.751 0 0 1-6 6.709v2.291h3a.75.75 0 0 1 0 1.5h-7.5a.75.75 0 0 1 0-1.5h3v-2.291a6.751 6.751 0 0 1-6-6.709v-1.5A.75.75 0 0 1 6 10.5Z" />
                  </svg>
                  Grabar Voz
                }
              </button>

              <label class="inline-flex cursor-pointer items-center gap-2 rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-slate-200">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="w-5 h-5 text-indigo-600">
                  <path fill-rule="evenodd" d="M1.5 6a2.25 2.25 0 0 1 2.25-2.25h16.5A2.25 2.25 0 0 1 22.5 6v12a2.25 2.25 0 0 1-2.25 2.25H3.75A2.25 2.25 0 0 1 1.5 18V6ZM3 16.06V18c0 .414.336.75.75.75h16.5A.75.75 0 0 0 21 18v-1.94l-2.69-2.689a1.5 1.5 0 0 0-2.12 0l-.88.879.97.97a.75.75 0 1 1-1.06 1.06l-5.16-5.159a1.5 1.5 0 0 0-2.12 0L3 16.061Zm10.125-7.81a1.125 1.125 0 1 1 2.25 0 1.125 1.125 0 0 1-2.25 0Z" clip-rule="evenodd" />
                </svg>
                Subir Imagen
                <input type="file" accept="image/jpeg,image/png,image/webp" (change)="selectFile($event, 'image')" class="hidden" />
              </label>

              @if (selectedFile) {
                <div class="flex items-center gap-2 rounded-lg bg-blue-50 px-3 py-1.5 text-sm text-blue-800 border border-blue-100">
                  <span class="truncate max-w-[200px] font-medium">{{ selectedFile.name }}</span>
                  <button type="button" (click)="clearSelection()" class="text-blue-500 hover:text-blue-700 hover:bg-blue-100 rounded-full p-0.5 transition" title="Quitar archivo">
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-4 h-4">
                      <path d="M6.28 5.22a.75.75 0 0 0-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 1 0 1.06 1.06L10 11.06l3.72 3.72a.75.75 0 1 0 1.06-1.06L11.06 10l3.72-3.72a.75.75 0 0 0-1.06-1.06L10 8.94 6.28 5.22Z" />
                    </svg>
                  </button>
                </div>
              }
            </div>
            
            <p class="text-xs text-slate-400 mt-1">
              Puedes escribir un texto, grabar un audio explicando tu situación, o subir una imagen (ej. tu CV).
            </p>
          </div>

          <!-- Submit Button -->
          <div class="mt-8 border-t border-slate-100 pt-6">
            <button
              type="button"
              [disabled]="loading || (!goal.trim() && !selectedFile)"
              (click)="ask()"
              class="inline-flex w-full items-center justify-center gap-2 rounded-xl bg-slate-900 px-6 py-3.5 text-sm font-semibold text-white shadow-sm transition duration-200 hover:bg-slate-800 focus:outline-none focus:ring-4 focus:ring-slate-900/20 disabled:cursor-not-allowed disabled:opacity-50 sm:w-auto"
            >
              @if (loading) {
                <svg class="h-4 w-4 animate-spin" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
                </svg>
                Analizando con IA...
              } @else {
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="w-4 h-4">
                  <path fill-rule="evenodd" d="M9 4.5a.75.75 0 0 1 .721.544l.813 2.846a3.75 3.75 0 0 0 2.576 2.576l2.846.813a.75.75 0 0 1 0 1.442l-2.846.813a3.75 3.75 0 0 0-2.576 2.576l-.813 2.846a.75.75 0 0 1-1.442 0l-.813-2.846a3.75 3.75 0 0 0-2.576-2.576l-2.846-.813a.75.75 0 0 1 0-1.442l2.846-.813A3.75 3.75 0 0 0 7.466 7.89l.813-2.846A.75.75 0 0 1 9 4.5ZM18 1.5a.75.75 0 0 1 .728.568l.258 1.036c.236.94.97 1.674 1.91 1.91l1.036.258a.75.75 0 0 1 0 1.456l-1.036.258c-.94.236-1.674.97-1.91 1.91l-.258 1.036a.75.75 0 0 1-1.456 0l-.258-1.036a2.625 2.625 0 0 0-1.91-1.91l-1.036-.258a.75.75 0 0 1 0-1.456l1.036-.258a2.625 2.625 0 0 0 1.91-1.91l.258-1.036A.75.75 0 0 1 18 1.5ZM16.5 15a.75.75 0 0 1 .712.513l.394 1.183c.15.447.5.799.948.948l1.183.395a.75.75 0 0 1 0 1.422l-1.183.395c-.447.15-.799.5-.948.948l-.395 1.183a.75.75 0 0 1-1.422 0l-.395-1.183a1.5 1.5 0 0 0-.948-.948l-1.183-.395a.75.75 0 0 1 0-1.422l1.183-.395c.447-.15.799-.5.948-.948l.395-1.183A.75.75 0 0 1 16.5 15Z" clip-rule="evenodd" />
                </svg>
                Pedir recomendación
              }
            </button>
          </div>

          <!-- Error Message -->
          @if (error) {
            <div class="mt-6 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
              <div class="font-medium">Ocurrió un error:</div>
              <div class="mt-1">{{ error }}</div>
            </div>
          }
        </div>
      </div>

      <!-- MODAL RESULTADO -->
      @if (showModal) {
        <div class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4 backdrop-blur-sm transition-opacity">
          <!-- Modal content -->
          <div class="relative w-full max-w-2xl max-h-[90vh] overflow-y-auto rounded-2xl bg-white p-6 shadow-2xl sm:p-8">
            
            <!-- Close button -->
            <button (click)="closeModal()" class="absolute top-4 right-4 rounded-full p-2 text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition">
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-6 h-6">
                <path stroke-linecap="round" stroke-linejoin="round" d="M6 18 18 6M6 6l12 12" />
              </svg>
            </button>

            <div class="mb-6 flex items-center gap-3">
              <div class="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-100 text-blue-700 shadow-sm">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="w-6 h-6">
                  <path fill-rule="evenodd" d="M9 4.5a.75.75 0 0 1 .721.544l.813 2.846a3.75 3.75 0 0 0 2.576 2.576l2.846.813a.75.75 0 0 1 0 1.442l-2.846.813a3.75 3.75 0 0 0-2.576 2.576l-.813 2.846a.75.75 0 0 1-1.442 0l-.813-2.846a3.75 3.75 0 0 0-2.576-2.576l-2.846-.813a.75.75 0 0 1 0-1.442l2.846-.813A3.75 3.75 0 0 0 7.466 7.89l.813-2.846A.75.75 0 0 1 9 4.5ZM18 1.5a.75.75 0 0 1 .728.568l.258 1.036c.236.94.97 1.674 1.91 1.91l1.036.258a.75.75 0 0 1 0 1.456l-1.036.258c-.94.236-1.674.97-1.91 1.91l-.258 1.036a.75.75 0 0 1-1.456 0l-.258-1.036a2.625 2.625 0 0 0-1.91-1.91l-1.036-.258a.75.75 0 0 1 0-1.456l1.036-.258a2.625 2.625 0 0 0 1.91-1.91l.258-1.036A.75.75 0 0 1 18 1.5ZM16.5 15a.75.75 0 0 1 .712.513l.394 1.183c.15.447.5.799.948.948l1.183.395a.75.75 0 0 1 0 1.422l-1.183.395c-.447.15-.799.5-.948.948l-.395 1.183a.75.75 0 0 1-1.422 0l-.395-1.183a1.5 1.5 0 0 0-.948-.948l-1.183-.395a.75.75 0 0 1 0-1.422l1.183-.395c.447-.15.799-.5.948-.948l.395-1.183A.75.75 0 0 1 16.5 15Z" clip-rule="evenodd" />
                </svg>
              </div>
              <h2 class="text-xl font-bold text-slate-900">
                Recomendación Personalizada
              </h2>
            </div>

            <!-- Transcription display if audio or image was used -->
            @if (sourceText && resultInputType !== 'TEXT') {
              <div class="mb-6 rounded-lg border border-slate-200 bg-slate-50 p-4">
                <h3 class="mb-1 text-xs font-bold uppercase tracking-wider text-slate-500">Lo que la IA detectó:</h3>
                <p class="text-sm italic text-slate-600">"{{ sourceText }}"</p>
              </div>
            }

            <!-- Main Explanation -->
            <div class="whitespace-pre-line text-sm leading-relaxed text-slate-700">
              {{ answer }}
            </div>

            <!-- Recommended Courses / Offerings -->
            @if (recommendations.length > 0) {
              <div class="mt-8">
                <h3 class="mb-4 text-sm font-bold text-slate-800">Cursos Sugeridos para ti:</h3>
                <div class="space-y-4">
                  @for (item of recommendations; track item.offeringId) {
                    <div class="flex flex-col gap-4 sm:flex-row sm:items-center justify-between rounded-xl border border-blue-100 bg-white p-4 shadow-sm transition hover:shadow-md hover:border-blue-200">
                      <div class="flex-1">
                        <p class="text-sm text-slate-600">{{ item.reason }}</p>
                        <span class="mt-2 inline-flex items-center rounded-full bg-green-50 px-2.5 py-0.5 text-xs font-semibold text-green-700 border border-green-200">
                          Relevancia: {{ (item.score * 100).toFixed(0) }}%
                        </span>
                      </div>
                      <button 
                        (click)="goToBooking(item.offeringId)"
                        class="shrink-0 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-600"
                      >
                        Agendar cita
                      </button>
                    </div>
                  }
                </div>
              </div>
            }
          </div>
        </div>
      }
    </section>
  `
})
export class AiComponent {
  goal = '';
  answer = '';
  error = '';
  selectedFile: File | null = null;
  selectedType: 'voice' | 'image' | null = null;
  
  resultInputType = ''; 
  sourceText = ''; 
  recommendations: { offeringId: string; score: number; reason: string }[] = [];
  
  loading = false;
  recording = false; 
  showModal = false;
  private recorder: MediaRecorder | null = null; 
  private chunks: Blob[] = [];

  constructor(private http: HttpClient, private router: Router) {}

  async toggleRecording() { 
    if (this.recording) { 
      this.recorder?.stop(); 
      return; 
    } 
    try { 
      const stream = await navigator.mediaDevices.getUserMedia({audio: true}); 
      this.chunks = []; 
      this.recorder = new MediaRecorder(stream); 
      this.recorder.ondataavailable = e => this.chunks.push(e.data); 
      this.recorder.onstop = () => { 
        const file = new File([new Blob(this.chunks, {type: this.recorder?.mimeType || 'audio/webm'})], 'nota-de-voz.webm', {type: this.recorder?.mimeType || 'audio/webm'}); 
        this.selectedFile = file; 
        this.selectedType = 'voice'; 
        this.recording = false; 
        stream.getTracks().forEach(t => t.stop()); 
      }; 
      this.recorder.start(); 
      this.recording = true; 
    } catch { 
      this.error = 'No fue posible acceder al micrófono. Verifica los permisos de tu navegador.'; 
    } 
  }

  selectFile(event: Event, type: 'voice' | 'image') { 
    const file = (event.target as HTMLInputElement).files?.[0] ?? null; 
    this.selectedFile = file; 
    this.selectedType = file ? type : null; 
  }
  
  clearSelection() {
    this.selectedFile = null;
    this.selectedType = null;
    this.goal = '';
  }

  closeModal() {
    this.showModal = false;
  }

  goToBooking(offeringId: string) {
    this.closeModal();
    this.router.navigate(['/book'], { queryParams: { offeringId } });
  }

  private formData() { 
    const data = new FormData(); 
    data.append('file', this.selectedFile!); 
    return data; 
  }

  ask() {
    this.error = '';
    this.answer = ''; 
    this.sourceText = ''; 
    this.recommendations = [];
    this.showModal = false;
    this.loading = true;

    const url = this.selectedFile 
      ? `${apiBase()}/ai/recommendations/${this.selectedType}` 
      : `${apiBase()}/ai/recommendations`;
      
    const payload = this.selectedFile 
      ? this.formData() 
      : { goal: this.goal };

    this.http.post<any>(url, payload).subscribe({
      next: (r) => {
        this.answer = r.explanation; 
        this.resultInputType = r.inputType; 
        this.sourceText = r.sourceText; 
        this.recommendations = r.recommendations ?? [];
        this.loading = false;
        this.showModal = true;
      },
      error: (e) => {
        this.error = e?.error?.detail || 'Ocurrió un problema procesando tu solicitud. Intenta de nuevo más tarde.';
        this.loading = false;
      }
    });
  }
}