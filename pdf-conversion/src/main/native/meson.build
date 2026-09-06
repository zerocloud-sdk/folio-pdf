project('folio-harfbuzz', 'c', version: '10.2.0', license: 'Apache-2.0',
  meson_version: '>=1.3.2',
  default_options: ['c_std=c11', 'warning_level=3', 'werror=true'])

engine = dependency('harfbuzz', version: '=10.2.0', method: 'pkg-config')
runtime_path = ''
if host_machine.system() == 'linux'
  runtime_path = '$ORIGIN/../lib'
elif host_machine.system() == 'darwin'
  runtime_path = '@loader_path/../lib'
endif

executable('folio-harfbuzz', 'folio-harfbuzz.c', dependencies: engine,
  install: true, install_rpath: runtime_path)
