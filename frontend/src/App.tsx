import { useRef, useState } from 'react'
import './App.css'

type FileStatus = 'Completado' | 'Pendiente' | 'Error'

type DataFile = {
  name: string
  date: string
  size: string
  status: FileStatus
  icon: string
}

const demoFiles: DataFile[] = [
  { name: 'sales_2026.xlsx', date: 'Hoy, 10:42 a. m.', size: '2.4 MB', status: 'Completado', icon: 'XLS' },
  { name: 'customers.csv', date: 'Ayer, 4:18 p. m.', size: '840 KB', status: 'Completado', icon: 'CSV' },
  { name: 'inventory_q3.xlsx', date: '10 oct. 2026', size: '1.2 MB', status: 'Pendiente', icon: 'XLS' },
]

const navigation = [
  { label: 'Resumen', icon: '▦' },
  { label: 'Mis archivos', icon: '▤' },
]

function App() {
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [activePage, setActivePage] = useState('Resumen')
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [notice, setNotice] = useState('')
  const [dragging, setDragging] = useState(false)

  const chooseFile = (file?: File) => {
    if (!file) return

    const extension = file.name.split('.').pop()?.toLowerCase()
    if (!['csv', 'xlsx'].includes(extension ?? '')) {
      setSelectedFile(null)
      setNotice('Formato no válido. Selecciona un archivo CSV o XLSX.')
      return
    }

    if (file.size > 10 * 1024 * 1024) {
      setSelectedFile(null)
      setNotice('El archivo supera el límite de 10 MB.')
      return
    }

    setSelectedFile(file)
    setNotice('Archivo seleccionado. La carga al servidor estará disponible en el siguiente paso.')
  }

  const openFilePicker = () => fileInputRef.current?.click()

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#" onClick={(event) => { event.preventDefault(); setActivePage('Resumen') }}>
          <span className="brand-mark"><span>↗</span></span>
          <span>data<span className="brand-light">flow</span></span>
        </a>

        <div className="workspace-label">ESPACIO DE TRABAJO</div>

        <nav className="sidebar-nav" aria-label="Navegación principal">
          {navigation.map((item) => (
            <button
              key={item.label}
              className={`nav-item ${activePage === item.label ? 'active' : ''}`}
              onClick={() => setActivePage(item.label)}
            >
              <span className="nav-icon">{item.icon}</span>
              {item.label}
              {item.label === 'Mis archivos' && <span className="nav-count">3</span>}
            </button>
          ))}
        </nav>

        <div className="sidebar-bottom">
          <div className="help-card">
            <div className="help-icon">✦</div>
            <strong>¿Primera vez aquí?</strong>
            <p>Sube tus datos y descubre qué contienen.</p>
            <button onClick={openFilePicker}>Analizar un archivo <span>→</span></button>
          </div>
          <div className="profile">
            <div className="avatar">DF</div>
            <div className="profile-copy">
              <strong>Mi espacio</strong>
              <span>Plan gratuito</span>
            </div>
            <span className="profile-menu">···</span>
          </div>
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <div className="breadcrumbs">
            <span>Workspace</span><span className="breadcrumb-separator">/</span>
            <strong>{activePage}</strong>
          </div>
          <div className="topbar-right">
            <span className="environment"><span /> Entorno de desarrollo</span>
            <button className="icon-button" aria-label="Notificaciones" title="Notificaciones">♧</button>
            <div className="top-avatar">DF</div>
          </div>
        </header>

        <div className="page-content">
          <section className="welcome-section">
            <div>
              <div className="eyebrow"><span className="eyebrow-dot" /> TU ESPACIO DE DATOS</div>
              <h1>{activePage === 'Resumen' ? 'Tus datos, más claros.' : 'Mis archivos.'}</h1>
              <p className="welcome-description">
                {activePage === 'Resumen'
                  ? 'Sube un archivo y deja que el análisis comience.'
                  : 'Consulta tus archivos y prepara tus próximos análisis.'}
              </p>
            </div>
            <button className="primary-button" onClick={openFilePicker}>
              <span className="button-plus">＋</span> Nuevo análisis
            </button>
          </section>

          <section className="stats-grid" aria-label="Resumen de actividad">
            <article className="stat-card">
              <div className="stat-top"><span>Archivos cargados</span><span className="stat-icon purple">▤</span></div>
              <div className="stat-value">03 <span className="stat-trend">archivos de ejemplo</span></div>
              <div className="stat-caption">Tus datos en un solo lugar</div>
            </article>
            <article className="stat-card">
              <div className="stat-top"><span>Análisis completados</span><span className="stat-icon green">✓</span></div>
              <div className="stat-value">02</div>
              <div className="stat-caption"><span className="tiny-green-dot" /> Archivos listos para consultar</div>
            </article>
            <article className="stat-card">
              <div className="stat-top"><span>Calidad de los datos</span><span className="stat-icon amber">◇</span></div>
              <div className="stat-value">— <span className="stat-trend">sin calcular</span></div>
              <div className="stat-caption">Se mostrará al conectar el análisis real</div>
            </article>
          </section>

          <section className="upload-section">
            <div className="section-heading">
              <div>
                <h2>Comienza con tus datos</h2>
                <p>Un archivo es todo lo que necesitas para empezar.</p>
              </div>
              <span className="step-label"><span>01</span> CARGA</span>
            </div>

            <div
              className={`upload-zone ${dragging ? 'dragging' : ''}`}
              onDragOver={(event) => { event.preventDefault(); setDragging(true) }}
              onDragLeave={() => setDragging(false)}
              onDrop={(event) => {
                event.preventDefault()
                setDragging(false)
                chooseFile(event.dataTransfer.files[0])
              }}
            >
              <input
                ref={fileInputRef}
                className="visually-hidden"
                type="file"
                accept=".csv,.xlsx"
                onChange={(event) => {
                  chooseFile(event.target.files?.[0])
                  event.target.value = ''
                }}
              />
              <div className="upload-icon"><span>↑</span></div>
              <h3>Arrastra tu archivo aquí</h3>
              <p>o selecciona un archivo desde tu equipo</p>
              <button className="secondary-button" onClick={openFilePicker}>Explorar archivos</button>
              <div className="file-requirements">
                <span><b>CSV</b> .csv</span>
                <span><b>XLSX</b> .xlsx</span>
                <span>Máximo 10 MB</span>
              </div>
            </div>

            {selectedFile && (
              <div className="selected-file">
                <span className="file-type-icon">{selectedFile.name.toLowerCase().endsWith('.csv') ? 'CSV' : 'XLS'}</span>
                <div className="selected-file-copy">
                  <strong>{selectedFile.name}</strong>
                  <span>{(selectedFile.size / (1024 * 1024)).toFixed(2)} MB · Seleccionado localmente</span>
                </div>
                <button className="remove-file" onClick={() => { setSelectedFile(null); setNotice('') }} aria-label="Quitar archivo">×</button>
              </div>
            )}
            {notice && <p className={`notice ${selectedFile ? 'notice-info' : 'notice-error'}`} role="status">{notice}</p>}
          </section>

          <section className="files-section">
            <div className="section-heading files-heading">
              <div>
                <h2>{activePage === 'Resumen' ? 'Archivos recientes' : 'Todos los archivos'}</h2>
                <p>Un vistazo rápido a tu actividad reciente.</p>
              </div>
              <button className="text-button" onClick={() => setActivePage(activePage === 'Resumen' ? 'Mis archivos' : 'Resumen')}>
                {activePage === 'Resumen' ? 'Ver todos' : 'Volver al resumen'} <span>→</span>
              </button>
            </div>

            <div className="files-table">
              <div className="table-header">
                <span>ARCHIVO</span><span>FECHA</span><span>TAMAÑO</span><span>ESTADO</span><span />
              </div>
              {demoFiles.map((file) => (
                <div className="file-row" key={file.name}>
                  <div className="file-name-cell">
                    <span className={`file-type-icon ${file.icon === 'CSV' ? 'csv-icon' : ''}`}>{file.icon}</span>
                    <div className="file-name-copy"><strong>{file.name}</strong><span>Archivo de ejemplo</span></div>
                  </div>
                  <span className="file-date">{file.date}</span>
                  <span className="file-size">{file.size}</span>
                  <span><span className={`status-pill ${file.status === 'Completado' ? 'status-complete' : file.status === 'Error' ? 'status-error' : 'status-pending'}`}><span />{file.status}</span></span>
                  <button className="row-menu" aria-label={`Opciones de ${file.name}`} title="Opciones">···</button>
                </div>
              ))}
              <div className="demo-disclaimer">Los archivos de esta tabla son ejemplos visuales; aún no corresponden a archivos guardados en tu cuenta.</div>
            </div>
          </section>

          <footer className="page-footer">
            <span>DATAFLOW <span className="footer-separator">·</span> INSIGHT STARTS HERE</span>
            <span>Construido para entender tus datos.</span>
          </footer>
        </div>
      </main>
    </div>
  )
}

export default App
