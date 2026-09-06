/*
 * Copyright ZeroCloud contributors. SPDX-License-Identifier: Apache-2.0
 * Project-owned adapter to HarfBuzz's public C API. No engine is bundled.
 * One bounded OPDQ/HRQ1 input produces one OPDR/HRS1 result, then exits.
 */
#include <hb.h>
#include <hb-ot.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#ifdef _WIN32
#include <fcntl.h>
#include <io.h>
#endif

#define MAX_PAYLOAD (64U * 1024U * 1024U)
#define MAX_TEXT (1024U * 1024U)
#define MAX_GLYPHS (1024U * 1024U)
#define CAPABILITY "composition.shaping.harf-buzz"

static uint32_t u32(const unsigned char *data) {
    return (uint32_t)data[0] << 24 | (uint32_t)data[1] << 16
            | (uint32_t)data[2] << 8 | data[3];
}

static int write32(uint32_t value) {
    unsigned char bytes[4];
    bytes[0] = (unsigned char)(value >> 24);
    bytes[1] = (unsigned char)(value >> 16);
    bytes[2] = (unsigned char)(value >> 8);
    bytes[3] = (unsigned char)value;
    return fwrite(bytes, 1, 4, stdout) == 4;
}

static int response(unsigned status, unsigned direction, unsigned units,
        unsigned count, const hb_glyph_info_t *info,
        const hb_glyph_position_t *positions) {
    unsigned major, minor, micro, index;
    hb_version(&major, &minor, &micro);
    if (!write32(0x4f504452U) || !write32(1) || !write32(0)
            || !write32(32U + count * 24U)
            || !write32(0x48525331U) || !write32(status)
            || !write32(major) || !write32(minor) || !write32(micro)
            || !write32(units) || !write32(direction) || !write32(count)) {
        return 0;
    }
    for (index = 0; index < count; index++) {
        if (!write32(info[index].codepoint) || !write32(info[index].cluster)
                || !write32((uint32_t)positions[index].x_advance)
                || !write32((uint32_t)positions[index].y_advance)
                || !write32((uint32_t)positions[index].x_offset)
                || !write32((uint32_t)positions[index].y_offset)) {
            return 0;
        }
    }
    return fflush(stdout) == 0;
}

static unsigned shape(const unsigned char *data, unsigned length) {
    uint32_t font_length, text_length, glyph_limit, direction, language_length;
    unsigned major, minor, micro, index, units, count = 0, status = 1;
    const unsigned char *font_data, *text_data;
    char script[5], language[64];
    uint16_t *text = NULL;
    hb_blob_t *blob = NULL;
    hb_face_t *face = NULL;
    hb_font_t *font = NULL;
    hb_buffer_t *buffer = NULL;
    hb_glyph_info_t *info = NULL;
    hb_glyph_position_t *positions = NULL;
    const char *shapers[] = { "ot", NULL };

    if (length < 28 || u32(data) != 0x48525131U) { return 0; }
    font_length = u32(data + 4);
    text_length = u32(data + 8);
    glyph_limit = u32(data + 12);
    direction = u32(data + 16);
    language_length = u32(data + 24);
    if (!font_length || !text_length || text_length > MAX_TEXT
            || !glyph_limit || glyph_limit > MAX_GLYPHS || direction > 1
            || !language_length || language_length > 63
            || 28ULL + font_length + 2ULL * text_length + language_length != length) {
        return 0;
    }
    memcpy(script, data + 20, 4);
    script[4] = 0;
    memcpy(language, data + 28, language_length);
    language[language_length] = 0;
    for (index = 0; index < 4; index++) {
        if (!((script[index] >= 'A' && script[index] <= 'Z')
                || (script[index] >= 'a' && script[index] <= 'z'))) { return 0; }
    }
    for (index = 0; index < language_length; index++) {
        char c = language[index];
        if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || (c == '-' && index > 0 && index + 1 < language_length
                        && language[index - 1] != '-'))) { return 0; }
    }
    hb_version(&major, &minor, &micro);
    if (major != HB_VERSION_MAJOR || minor != HB_VERSION_MINOR || micro != HB_VERSION_MICRO
            || strcmp(hb_version_string(), HB_VERSION_STRING) != 0) {
        return response(3, direction, 0, 0, NULL, NULL);
    }
    font_data = data + 28 + language_length;
    text_data = font_data + font_length;
    text = (uint16_t *)malloc(text_length * sizeof(uint16_t));
    if (!text) { return response(4, direction, 0, 0, NULL, NULL); }
    for (index = 0; index < text_length; index++) {
        text[index] = (uint16_t)((unsigned)text_data[2 * index] << 8
                | text_data[2 * index + 1]);
    }
    for (index = 0; index < text_length; index++) {
        if (text[index] >= 0xd800 && text[index] <= 0xdbff) {
            if (++index == text_length || text[index] < 0xdc00 || text[index] > 0xdfff) {
                goto cleanup;
            }
        } else if (text[index] >= 0xdc00 && text[index] <= 0xdfff) { goto cleanup; }
    }

    blob = hb_blob_create((const char *)font_data, font_length,
            HB_MEMORY_MODE_READONLY, NULL, NULL);
    face = hb_face_create(blob, 0);
    units = hb_face_get_upem(face);
    if (!hb_face_get_glyph_count(face) || units < 16 || units > 16384) { goto cleanup; }
    font = hb_font_create(face);
    hb_ot_font_set_funcs(font);
    hb_font_set_scale(font, (int)units, (int)units);
    buffer = hb_buffer_create();
    hb_buffer_set_direction(buffer, direction ? HB_DIRECTION_RTL : HB_DIRECTION_LTR);
    hb_buffer_set_script(buffer, hb_script_from_string(script, 4));
    hb_buffer_set_language(buffer, hb_language_from_string(language, (int)language_length));
    hb_buffer_set_cluster_level(buffer, HB_BUFFER_CLUSTER_LEVEL_MONOTONE_GRAPHEMES);
    hb_buffer_add_utf16(buffer, text, (int)text_length, 0, (int)text_length);
    if (!hb_buffer_allocation_successful(buffer)
            || !hb_shape_full(font, buffer, NULL, 0, shapers)
            || !hb_buffer_allocation_successful(buffer)) { status = 4; goto cleanup; }
    count = hb_buffer_get_length(buffer);
    if (count > glyph_limit) { status = 2; count = 0; goto cleanup; }
    if (!count) { goto cleanup; }
    info = hb_buffer_get_glyph_infos(buffer, NULL);
    positions = hb_buffer_get_glyph_positions(buffer, NULL);
    status = 0;

cleanup:
    index = (unsigned)response(status, direction, status ? 0 : units,
            status ? 0 : count, info, positions);
    if (buffer) { hb_buffer_destroy(buffer); }
    if (font) { hb_font_destroy(font); }
    if (face) { hb_face_destroy(face); }
    if (blob) { hb_blob_destroy(blob); }
    free(text);
    return index;
}

int main(void) {
    unsigned char header[10], capability[sizeof(CAPABILITY) - 1], size[8];
    unsigned char *payload;
    uint32_t length;
    unsigned success;
#ifdef _WIN32
    if (_setmode(_fileno(stdin), _O_BINARY) == -1
            || _setmode(_fileno(stdout), _O_BINARY) == -1) { return EXIT_FAILURE; }
#endif
    if (fread(header, 1, sizeof(header), stdin) != sizeof(header)
            || u32(header) != 0x4f504451U || u32(header + 4) != 1
            || header[8] != 0 || header[9] != sizeof(capability)
            || fread(capability, 1, sizeof(capability), stdin) != sizeof(capability)
            || memcmp(capability, CAPABILITY, sizeof(capability)) != 0
            || fread(size, 1, sizeof(size), stdin) != sizeof(size)
            || u32(size) != 0) { return EXIT_FAILURE; }
    length = u32(size + 4);
    if (length < 28 || length > MAX_PAYLOAD) { return EXIT_FAILURE; }
    payload = (unsigned char *)malloc(length);
    if (!payload) { return EXIT_FAILURE; }
    if (fread(payload, 1, length, stdin) != length || fgetc(stdin) != EOF || ferror(stdin)) {
        free(payload);
        return EXIT_FAILURE;
    }
    success = shape(payload, length);
    free(payload);
    return success ? EXIT_SUCCESS : EXIT_FAILURE;
}
